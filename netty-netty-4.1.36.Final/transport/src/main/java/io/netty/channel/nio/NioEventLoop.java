/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopException;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReflectionUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SingleThreadEventLoop} implementation which register the {@link Channel}'s to a
 * {@link Selector} and so does the multi-plexing of these in the event loop.
 *
 */
public final class NioEventLoop extends SingleThreadEventLoop {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioEventLoop.class);

    private static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    private static final boolean DISABLE_KEY_SET_OPTIMIZATION =
            SystemPropertyUtil.getBoolean("io.netty.noKeySetOptimization", false);

    private static final int MIN_PREMATURE_SELECTOR_RETURNS = 3;
    private static final int SELECTOR_AUTO_REBUILD_THRESHOLD;

    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            // 执行非阻塞选择
            return selectNow();
        }
    };

    // Workaround for JDK NIO bug.
    //
    // See:
    // - http://bugs.sun.com/view_bug.do?bug_id=6427854
    // - https://github.com/netty/netty/issues/203
    static {
        final String key = "sun.nio.ch.bugLevel";
        final String bugLevel = SystemPropertyUtil.get(key);
        if (bugLevel == null) {
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        System.setProperty(key, "");
                        return null;
                    }
                });
            } catch (final SecurityException e) {
                logger.debug("Unable to get/set System Property: " + key, e);
            }
        }

        int selectorAutoRebuildThreshold = SystemPropertyUtil.getInt("io.netty.selectorAutoRebuildThreshold", 512);
        if (selectorAutoRebuildThreshold < MIN_PREMATURE_SELECTOR_RETURNS) {
            selectorAutoRebuildThreshold = 0;
        }

        SELECTOR_AUTO_REBUILD_THRESHOLD = selectorAutoRebuildThreshold;

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.noKeySetOptimization: {}", DISABLE_KEY_SET_OPTIMIZATION);
            logger.debug("-Dio.netty.selectorAutoRebuildThreshold: {}", SELECTOR_AUTO_REBUILD_THRESHOLD);
        }
    }

    /**
     * The NIO {@link Selector}.
     */
    private Selector selector;
    private Selector unwrappedSelector;
    private SelectedSelectionKeySet selectedKeys;

    private final SelectorProvider provider;

    /**
     * Boolean that controls determines if a blocked Selector.select should
     * break out of its selection process. In our case we use a timeout for
     * the select method and the select method will block for that time unless
     * waken up.
     */
    private final AtomicBoolean wakenUp = new AtomicBoolean();

    private final SelectStrategy selectStrategy;

    private volatile int ioRatio = 50;
    private int cancelledKeys;
    private boolean needsToSelectAgain;

    NioEventLoop(NioEventLoopGroup parent, Executor executor, SelectorProvider selectorProvider,
                 SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, false, DEFAULT_MAX_PENDING_TASKS, rejectedExecutionHandler);
        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        if (strategy == null) {
            throw new NullPointerException("selectStrategy");
        }
        provider = selectorProvider;
        // 创建一个selector二元组实例
        final SelectorTuple selectorTuple = openSelector();
        selector = selectorTuple.selector;
        unwrappedSelector = selectorTuple.unwrappedSelector;
        selectStrategy = strategy;
    }

    private static final class SelectorTuple {
        // NIO原生的selector
        final Selector unwrappedSelector;
        // netty优化过的selector
        final Selector selector;

        SelectorTuple(Selector unwrappedSelector) {
            this.unwrappedSelector = unwrappedSelector;
            this.selector = unwrappedSelector;
        }

        SelectorTuple(Selector unwrappedSelector, Selector selector) {
            this.unwrappedSelector = unwrappedSelector;
            this.selector = selector;
        }
    }

    private SelectorTuple openSelector() {
        final Selector unwrappedSelector;
        try {
            // 获取到NIO原生selector
            unwrappedSelector = provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }
        // 若禁用了keySet优化，则直接返回一个仅封装了NIO原生selector的Tuple
        if (DISABLE_KEY_SET_OPTIMIZATION) {
            return new SelectorTuple(unwrappedSelector);
        }
        // 下面的代码就是优化Selector的三个Set：keySet、selectedKeySet、cancelledKeySet\
        // 如何优化的呢？将Set集合转变为数组
        Object maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    return Class.forName(
                            "sun.nio.ch.SelectorImpl",
                            false,
                            PlatformDependent.getSystemClassLoader());
                } catch (Throwable cause) {
                    return cause;
                }
            }
        });

        if (!(maybeSelectorImplClass instanceof Class) ||
            // ensure the current selector implementation is what we can instrument.
            !((Class<?>) maybeSelectorImplClass).isAssignableFrom(unwrappedSelector.getClass())) {
            if (maybeSelectorImplClass instanceof Throwable) {
                Throwable t = (Throwable) maybeSelectorImplClass;
                logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, t);
            }
            return new SelectorTuple(unwrappedSelector);
        }

        final Class<?> selectorImplClass = (Class<?>) maybeSelectorImplClass;
        final SelectedSelectionKeySet selectedKeySet = new SelectedSelectionKeySet();

        Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    Field selectedKeysField = selectorImplClass.getDeclaredField("selectedKeys");
                    Field publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys");

                    if (PlatformDependent.javaVersion() >= 9 && PlatformDependent.hasUnsafe()) {
                        // Let us try to use sun.misc.Unsafe to replace the SelectionKeySet.
                        // This allows us to also do this in Java9+ without any extra flags.
                        long selectedKeysFieldOffset = PlatformDependent.objectFieldOffset(selectedKeysField);
                        long publicSelectedKeysFieldOffset =
                                PlatformDependent.objectFieldOffset(publicSelectedKeysField);

                        if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                            PlatformDependent.putObject(
                                    unwrappedSelector, selectedKeysFieldOffset, selectedKeySet);
                            PlatformDependent.putObject(
                                    unwrappedSelector, publicSelectedKeysFieldOffset, selectedKeySet);
                            return null;
                        }
                        // We could not retrieve the offset, lets try reflection as last-resort.
                    }

                    Throwable cause = ReflectionUtil.trySetAccessible(selectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }
                    cause = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }

                    selectedKeysField.set(unwrappedSelector, selectedKeySet);
                    publicSelectedKeysField.set(unwrappedSelector, selectedKeySet);
                    return null;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                }
            }
        });

        if (maybeException instanceof Exception) {
            selectedKeys = null;
            Exception e = (Exception) maybeException;
            logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, e);
            return new SelectorTuple(unwrappedSelector);
        }
        selectedKeys = selectedKeySet;
        logger.trace("instrumented a special java.util.Set into: {}", unwrappedSelector);
        return new SelectorTuple(unwrappedSelector,
                                 new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet));
    }

    /**
     * Returns the {@link SelectorProvider} used by this {@link NioEventLoop} to obtain the {@link Selector}.
     */
    public SelectorProvider selectorProvider() {
        return provider;
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                                                    : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    /**
     * Registers an arbitrary {@link SelectableChannel}, not necessarily created by Netty, to the {@link Selector}
     * of this event loop.  Once the specified {@link SelectableChannel} is registered, the specified {@code task} will
     * be executed by this event loop when the {@link SelectableChannel} is ready.
     */
    public void register(final SelectableChannel ch, final int interestOps, final NioTask<?> task) {
        if (ch == null) {
            throw new NullPointerException("ch");
        }
        if (interestOps == 0) {
            throw new IllegalArgumentException("interestOps must be non-zero.");
        }
        if ((interestOps & ~ch.validOps()) != 0) {
            throw new IllegalArgumentException(
                    "invalid interestOps: " + interestOps + "(validOps: " + ch.validOps() + ')');
        }
        if (task == null) {
            throw new NullPointerException("task");
        }

        if (isShutdown()) {
            throw new IllegalStateException("event loop shut down");
        }

        if (inEventLoop()) {
            register0(ch, interestOps, task);
        } else {
            try {
                // Offload to the EventLoop as otherwise java.nio.channels.spi.AbstractSelectableChannel.register
                // may block for a long time while trying to obtain an internal lock that may be hold while selecting.
                submit(new Runnable() {
                    @Override
                    public void run() {
                        register0(ch, interestOps, task);
                    }
                }).sync();
            } catch (InterruptedException ignore) {
                // Even if interrupted we did schedule it so just mark the Thread as interrupted.
                Thread.currentThread().interrupt();
            }
        }
    }

    private void register0(SelectableChannel ch, int interestOps, NioTask<?> task) {
        try {
            ch.register(unwrappedSelector, interestOps, task);
        } catch (Exception e) {
            throw new EventLoopException("failed to register a channel", e);
        }
    }

    /**
     * Returns the percentage of the desired amount of time spent for I/O in the event loop.
     */
    public int getIoRatio() {
        return ioRatio;
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the event loop.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        if (ioRatio <= 0 || ioRatio > 100) {
            throw new IllegalArgumentException("ioRatio: " + ioRatio + " (expected: 0 < ioRatio <= 100)");
        }
        this.ioRatio = ioRatio;
    }

    /**
     * Replaces the current {@link Selector} of this event loop with newly created {@link Selector}s to work
     * around the infamous epoll 100% CPU bug.
     */
    public void rebuildSelector() {
        if (!inEventLoop()) {
            execute(new Runnable() {
                @Override
                public void run() {
                    rebuildSelector0();
                }
            });
            return;
        }
        rebuildSelector0();
    }

    @Override
    public int registeredChannels() {
        return selector.keys().size() - cancelledKeys;
    }

    private void rebuildSelector0() {
        final Selector oldSelector = selector;
        final SelectorTuple newSelectorTuple;

        if (oldSelector == null) {
            return;
        }

        try {
            newSelectorTuple = openSelector();
        } catch (Exception e) {
            logger.warn("Failed to create a new Selector.", e);
            return;
        }

        // Register all channels to the new Selector.
        int nChannels = 0;
        for (SelectionKey key: oldSelector.keys()) {
            Object a = key.attachment();
            try {
                if (!key.isValid() || key.channel().keyFor(newSelectorTuple.unwrappedSelector) != null) {
                    continue;
                }

                int interestOps = key.interestOps();
                key.cancel();
                SelectionKey newKey = key.channel().register(newSelectorTuple.unwrappedSelector, interestOps, a);
                if (a instanceof AbstractNioChannel) {
                    // Update SelectionKey
                    ((AbstractNioChannel) a).selectionKey = newKey;
                }
                nChannels ++;
            } catch (Exception e) {
                logger.warn("Failed to re-register a Channel to the new Selector.", e);
                if (a instanceof AbstractNioChannel) {
                    AbstractNioChannel ch = (AbstractNioChannel) a;
                    ch.unsafe().close(ch.unsafe().voidPromise());
                } else {
                    @SuppressWarnings("unchecked")
                    NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                    invokeChannelUnregistered(task, key, e);
                }
            }
        }

        selector = newSelectorTuple.selector;
        unwrappedSelector = newSelectorTuple.unwrappedSelector;

        try {
            // time to close the old selector as everything else is registered to the new one
            oldSelector.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close the old Selector.", t);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Migrated " + nChannels + " channel(s) to the new Selector.");
        }
    }

    @Override
    protected void run() {
        // 执行一个无限循环，在没有发生异常的情况下，其会永远执行下去
        for (;;) {
            try {
                try {
                    // ---------------------------- 1 选择就绪channel --------------
                    switch (selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())) {
                    case SelectStrategy.CONTINUE:  // 对于NioEventLoop，其不支持该case
                        continue;

                    case SelectStrategy.BUSY_WAIT:  // 对于NioEventLoop，其不支持该case
                        // fall-through to SELECT since the busy-wait is not supported with NIO

                    case SelectStrategy.SELECT:
                        // 若当前任务队列中没有任务，则会执行该case，即执行阻塞式选择
                        select(wakenUp.getAndSet(false));

                        // 'wakenUp.compareAndSet(false, true)' is always evaluated
                        // before calling 'selector.wakeup()' to reduce the wake-up
                        // overhead. (Selector.wakeup() is an expensive operation.)
                        //
                        // However, there is a race condition in this approach.
                        // The race condition is triggered when 'wakenUp' is set to
                        // true too early.
                        //
                        // 'wakenUp' is set to true too early if:
                        // 1) Selector is waken up between 'wakenUp.set(false)' and
                        //    'selector.select(...)'. (BAD)
                        // 2) Selector is waken up between 'selector.select(...)' and
                        //    'if (wakenUp.get()) { ... }'. (OK)
                        //
                        // In the first case, 'wakenUp' is set to true and the
                        // following 'selector.select(...)' will wake up immediately.
                        // Until 'wakenUp' is set to false again in the next round,
                        // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                        // any attempt to wake up the Selector will fail, too, causing
                        // the following 'selector.select(...)' call to block
                        // unnecessarily.
                        //
                        // To fix this problem, we wake up the selector again if wakenUp
                        // is true immediately after selector.select(...).
                        // It is inefficient in that it wakes up the selector for both
                        // the first case (BAD - wake-up required) and the second case
                        // (OK - no wake-up required).

                        if (wakenUp.get()) {
                            // 立即获取选择的结果
                            selector.wakeup();
                        }
                        // fall through
                    default:
                    }
                } catch (IOException e) {
                    // If we receive an IOException here its because the Selector is messed up. Let's rebuild
                    // the selector and retry. https://github.com/netty/netty/issues/8566
                    rebuildSelector0();
                    handleLoopException(e);
                    continue;
                }

                // -------------------------------- 2 处理就绪channel的IO ------------------
                cancelledKeys = 0;
                needsToSelectAgain = false;
                // 该值的取值范围为(0,100]
                final int ioRatio = this.ioRatio;
                if (ioRatio == 100) {
                    try {
                        processSelectedKeys();
                    } finally {
                        // Ensure we always run tasks.
                        runAllTasks();
                    }
                } else {
                    // 获取当前时间，即就绪channel的IO开始执行的时间点
                    final long ioStartTime = System.nanoTime();
                    try {
                        // 处理就绪channel的IO
                        processSelectedKeys();
                    } finally {
                        // -------------------------------- 3 处理任务队列中的任务 ------------------
                        // Ensure we always run tasks.
                        // 计算出处理就绪channel的IO所用的时间
                        final long ioTime = System.nanoTime() - ioStartTime;
                        // ioTime * (100 - ioRatio) / ioRatio 为任务队列中的任务执行可以使用的时长
                        runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                    }
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
            // Always handle shutdown even if the loop processing threw an exception.
            try {
                if (isShuttingDown()) {
                    closeAll();
                    if (confirmShutdown()) {
                        return;
                    }
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
        }  // end-for
    }

    private static void handleLoopException(Throwable t) {
        logger.warn("Unexpected exception in the selector loop.", t);

        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    private void processSelectedKeys() {
        // 若优化过的set不为null，
        if (selectedKeys != null) {
            // 处理优化过的SelectedKeys
            processSelectedKeysOptimized();
        } else {
            // 处理普通的SelectedKeys集合
            // selector.selectedKeys() 为set集合
            processSelectedKeysPlain(selector.selectedKeys());
        }
    }

    @Override
    protected void cleanup() {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn("Failed to close a selector.", e);
        }
    }

    void cancel(SelectionKey key) {
        key.cancel();
        cancelledKeys ++;
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            needsToSelectAgain = true;
        }
    }

    @Override
    protected Runnable pollTask() {
        Runnable task = super.pollTask();
        if (needsToSelectAgain) {
            selectAgain();
        }
        return task;
    }

    private void processSelectedKeysPlain(Set<SelectionKey> selectedKeys) {
        // check if the set is empty and if so just return to not create garbage by
        // creating a new Iterator every time even if there is nothing to process.
        // See https://github.com/netty/netty/issues/597
        if (selectedKeys.isEmpty()) {
            return;
        }
        // 迭代集合
        Iterator<SelectionKey> i = selectedKeys.iterator();
        for (;;) {
            final SelectionKey k = i.next();
            final Object a = k.attachment();
            i.remove();

            if (a instanceof AbstractNioChannel) {
                processSelectedKey(k, (AbstractNioChannel) a);
            } else {
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                processSelectedKey(k, task);
            }

            if (!i.hasNext()) {
                break;
            }

            if (needsToSelectAgain) {
                selectAgain();
                selectedKeys = selector.selectedKeys();

                // Create the iterator again to avoid ConcurrentModificationException
                if (selectedKeys.isEmpty()) {
                    break;
                } else {
                    i = selectedKeys.iterator();
                }
            }
        }
    }

    private void processSelectedKeysOptimized() {
        for (int i = 0; i < selectedKeys.size; ++i) {
            final SelectionKey k = selectedKeys.keys[i];
            // null out entry in the array to allow to have it GC'ed once the Channel close
            // See https://github.com/netty/netty/issues/2363
            // 赋值为null的原因有两个：
            // 1)当channel被关闭时便于做GC
            // 2)避免重复处理
            selectedKeys.keys[i] = null;
            // 获取到key中的附件，附件中可以写入任何数据，
            // 不过，对于NioEventLoop，其附件中存放的是原生channel
            final Object a = k.attachment();

            if (a instanceof AbstractNioChannel) {
                // 处理selectedKey
                processSelectedKey(k, (AbstractNioChannel) a);
            } else {
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                processSelectedKey(k, task);
            }

            if (needsToSelectAgain) {
                // null out entries in the array to allow to have it GC'ed once the Channel close
                // See https://github.com/netty/netty/issues/2363
                selectedKeys.reset(i + 1);

                selectAgain();
                i = -1;
            }
        }
    }

    private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
        final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
        // 处理key失效的情况
        if (!k.isValid()) {
            final EventLoop eventLoop;
            try {
                eventLoop = ch.eventLoop();
            } catch (Throwable ignored) {
                // If the channel implementation throws an exception because there is no event loop, we ignore this
                // because we are only trying to determine if ch is registered to this event loop and thus has authority
                // to close ch.
                return;
            }
            // Only close ch if ch is still registered to this EventLoop. ch could have deregistered from the event loop
            // and thus the SelectionKey could be cancelled as part of the deregistration process, but the channel is
            // still healthy and should not be closed.
            // See https://github.com/netty/netty/issues/5125
            if (eventLoop != this || eventLoop == null) {
                return;
            }
            // close the channel if the key is not valid anymore
            unsafe.close(unsafe.voidPromise());
            return;
        }
        try {
            // 获取当前selectionKey（即就绪channel）的就绪事件
            int readyOps = k.readyOps();
            // We first need to call finishConnect() before try to trigger a read(...) or write(...) as otherwise
            // the NIO JDK channel implementation may throw a NotYetConnectedException.
            // 处理连接就绪的情况（Server端不会出现该情况，只有Client才会发起连接Server的请求）
            // 当第一个连接请求发出后，若连接成功，则成功；否则，连接就绪
            // 连接就绪后该channel就会被selector选择。一旦选择了该channel，则处理就绪IO，
            // 连接就绪的IO就是去连接Server端，完成连接
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
                // See https://github.com/netty/netty/issues/924
                // 获取当前selectionKey中注册的当前channel所关注的就绪事件
                int ops = k.interestOps();
                // 将ops中的连接就绪位置0
                ops &= ~SelectionKey.OP_CONNECT;
                // 将置0后的ops再写入到selectionKey中，表示当前连接就绪已经处理完毕
                k.interestOps(ops);
                // 完成连接
                unsafe.finishConnect();
            }
            // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
            // 处理写就绪的情况
            // 什么是写就绪？当调用了writeAndFlush()后，将所有要发送的数据写入到用户缓存后，
            // 写操作就绪了
            // 写操作就绪后就可以处理就绪IO了。写操作就绪IO是，将用户缓存中的数据写入到网卡缓存中
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                // Call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to write
                ch.unsafe().forceFlush();
            }
            // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
            // to a spin loop
            // 处理读就绪与接收连接就绪的情况
            // 读就绪是什么？就是通过网络client已经将数据写入到了网卡缓存。
            // 接收连接就绪是什么？就是通过网络client已经将连接请求写入到了网卡缓存。
            // 故，接收连接就绪其实就是读就绪的一种特例，因此，无论是读就绪还是接收连接就绪，
            // 一旦就绪，就可以处理就绪的IO了。这些就绪IO就是将网卡缓存中的数据（有可能是连接请求）读取
            // 到用户缓存，即会出现在channelRead()方法的msg变量中

            // readyOps为0表示没有任何channel就绪，没有就绪的channel，为何要执行读就绪IO？
            // 两个原因：
            // 1)为了避免NIO的BUG
            // 2)为了使没有就绪channel的IO执行时间拉长一些，从而使后面的任务队列中任务的执行时间不至于太短
            if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                unsafe.read();
            }
        } catch (CancelledKeyException ignored) {
            unsafe.close(unsafe.voidPromise());
        }
    }

    private static void processSelectedKey(SelectionKey k, NioTask<SelectableChannel> task) {
        int state = 0;
        try {
            task.channelReady(k.channel(), k);
            state = 1;
        } catch (Exception e) {
            k.cancel();
            invokeChannelUnregistered(task, k, e);
            state = 2;
        } finally {
            switch (state) {
            case 0:
                k.cancel();
                invokeChannelUnregistered(task, k, null);
                break;
            case 1:
                if (!k.isValid()) { // Cancelled by channelReady()
                    invokeChannelUnregistered(task, k, null);
                }
                break;
            }
        }
    }

    private void closeAll() {
        selectAgain();
        Set<SelectionKey> keys = selector.keys();
        Collection<AbstractNioChannel> channels = new ArrayList<AbstractNioChannel>(keys.size());
        for (SelectionKey k: keys) {
            Object a = k.attachment();
            if (a instanceof AbstractNioChannel) {
                channels.add((AbstractNioChannel) a);
            } else {
                k.cancel();
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                invokeChannelUnregistered(task, k, null);
            }
        }

        for (AbstractNioChannel ch: channels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    private static void invokeChannelUnregistered(NioTask<SelectableChannel> task, SelectionKey k, Throwable cause) {
        try {
            task.channelUnregistered(k.channel(), cause);
        } catch (Exception e) {
            logger.warn("Unexpected exception while running NioTask.channelUnregistered()", e);
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && wakenUp.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }

    Selector unwrappedSelector() {
        return unwrappedSelector;
    }

    int selectNow() throws IOException {
        try {
            // selector执行非阻塞就绪channel的选择，其值是>=0的
            return selector.selectNow();
        } finally {
            // restore wakeup state if needed
            // wakenUp的值为布尔值
            // true：表示当前eventLoop所绑定的线程处于唤醒状态，即非阻塞状态
            // false：表示当前eventLoop所绑定线程处于或即将处于非唤醒状态，即阻塞状态
            if (wakenUp.get()) {
                // 立即返回选择的结果，即马上修改selector中的selectedKeys集合
                selector.wakeup();
            }
        }
    }

    private void select(boolean oldWakenUp) throws IOException {
        Selector selector = this.selector;
        try {
            // 计数器：
            // 为0，表示当前尚未有选择出的就绪channel
            // 非0，表示已经执行过了选择操作
            int selectCnt = 0;
            // 当前时间，其也是for循环第一轮循环的起始时间点
            long currentTimeNanos = System.nanoTime();
            // delayNanos() 表示定时任务队列中第一个定时任务距离开始执行时间还有多久
            // selectDeadLineNanos 表示定时任务队列中第一个定时任务开始执行的时间点
            long selectDeadLineNanos = currentTimeNanos + delayNanos(currentTimeNanos);

            for (;;) {
                // ------------------- 1 处理定时任务队列中的马上就到执行时间的第一个定时任务 -----------
                // 500000L 表示0.5毫秒    1000000L 表示1毫秒
                // “马上”是多久？小于0.5毫秒
                long timeoutMillis = (selectDeadLineNanos - currentTimeNanos + 500000L) / 1000000L;
                if (timeoutMillis <= 0) {  // 为true表示当前具有马上就到其的任务要执行
                    // 在结束当前select()方法之前，随带着查看一下是否有就绪的channel
                    if (selectCnt == 0) {
                        // 非阻塞选择
                        selector.selectNow();
                        selectCnt = 1;
                    }
                    break;
                }

                // 代码能走到这里，说明当前没有【马上】要执行的定时任务
                // ------------------- 2 处理具有新添加任务的情况 -----------
                // If a task was submitted when wakenUp value was true, the task didn't get a chance to call
                // Selector#wakeup. So we need to check task queue again before executing select operation.
                // If we don't, the task might be pended until select operation was timed out.
                // It might be pended until idle timeout if IdleStateHandler existed in pipeline.
                // 代码能运行当前的select()方法，是由于当时没有任务要执行。但仅仅是当时没有，并不代表一直没有。
                // 这里就是处理当前具有了新的任务的情况，有任务就要先执行任务，所以要结果当前select()的执行。
                // 不过，在结束之前，随带着查看一下是否有就绪的channel
                if (hasTasks() && wakenUp.compareAndSet(false, true)) {
                    // 非阻塞选择
                    selector.selectNow();
                    selectCnt = 1;
                    break;  // 结束当前的select()
                }

                // 代码能走到这里说明当前既没有【马上】要执行的定时任务，又没有新的任务添加
                // ------------------- 3 处理阻塞式选择的情况 -----------
                // 阻塞式选择，其阻塞被唤醒的条件有五个：
                // 1)发现就绪channel
                // 2)selector的wakeup()方法被调用
                // 3)当前线程被打断
                // 4)阻塞时间超时
                // 5)当出现大量的空轮询时，会使CPU占用率急剧飙升，出于对系统的保护，该方法会提前终止
                int selectedKeys = selector.select(timeoutMillis);

                selectCnt ++;
                // 若有就绪channel了，或wakeup()方法被调用了，或有了任务了，则直接结束select()
                if (selectedKeys != 0 || oldWakenUp || wakenUp.get() || hasTasks() || hasScheduledTasks()) {
                    // - Selected something,
                    // - waken up by user, or
                    // - the task queue has a pending task.
                    // - a scheduled task is ready for processing
                    break;
                }
                // 代码能走到这里，说明selector.select()的结束已经排除了第1）2）两种情况
                if (Thread.interrupted()) {
                    // Thread was interrupted so reset selected keys and break so we not run into a busy loop.
                    // As this is most likely a bug in the handler of the user or it's client library we will
                    // also log it.
                    //
                    // See https://github.com/netty/netty/issues/2426
                    if (logger.isDebugEnabled()) {
                        logger.debug("Selector.select() returned prematurely because " +
                                "Thread.currentThread().interrupt() was called. Use " +
                                "NioEventLoop.shutdownGracefully() to shutdown the NioEventLoop.");
                    }
                    selectCnt = 1;
                    break;
                }

                // 代码能走到这里，说明selector.select()的结束已经排除了第1）2）3）三种情况，
                // 即selector.select()的结束原因只可能是4）5）两种情况之一了
                // ------------------- 4 解决NIO的Bug -----------
                long time = System.nanoTime();

                // 若该条件为true，则表示selector.select()是由于第4）种情况结束的
                if (time - TimeUnit.MILLISECONDS.toNanos(timeoutMillis) >= currentTimeNanos) {
                    // timeoutMillis elapsed without anything selected.
                    selectCnt = 1;

                    // 若该条件为false，则表示selector.select()是由于第5）种情况结束的
                } else if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&
                        selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
                    // The code exists in an extra method to ensure the method is not too big to inline as this
                    // branch is not very likely to get hit very frequently.
                    // 重构seletor
                    selector = selectRebuildSelector(selectCnt);
                    selectCnt = 1;
                    break;
                }
                // 更新currentTimeNanos为当前时间，即记录下一轮for循环开始的时间点
                currentTimeNanos = time;
            }  // end-for

            if (selectCnt > MIN_PREMATURE_SELECTOR_RETURNS) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Selector.select() returned prematurely {} times in a row for Selector {}.",
                            selectCnt - 1, selector);
                }
            }
        } catch (CancelledKeyException e) {
            if (logger.isDebugEnabled()) {
                logger.debug(CancelledKeyException.class.getSimpleName() + " raised by a Selector {} - JDK bug?",
                        selector, e);
            }
            // Harmless exception - log anyway
        }
    }

    private Selector selectRebuildSelector(int selectCnt) throws IOException {
        // The selector returned prematurely many times in a row.
        // Rebuild the selector to work around the problem.
        logger.warn(
                "Selector.select() returned prematurely {} times in a row; rebuilding Selector {}.",
                selectCnt, selector);

        rebuildSelector();
        Selector selector = this.selector;

        // Select again to populate selectedKeys.
        selector.selectNow();
        return selector;
    }

    private void selectAgain() {
        needsToSelectAgain = false;
        try {
            selector.selectNow();
        } catch (Throwable t) {
            logger.warn("Failed to update SelectionKeys.", t);
        }
    }
}
