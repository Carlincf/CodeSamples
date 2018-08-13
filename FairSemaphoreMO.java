package edu.vandy.simulator.managers.palantiri.concurrentMapFairSemaphore;

import java.util.LinkedList;

public class FairSemaphoreMO
        implements FairSemaphore {
    /**
     * Debugging tag used by the Android logger.
     */
    protected final static String TAG =
            FairSemaphoreMO.class.getSimpleName();

    /**
     * A count of the number of available permits,
     * ensureing that values aren't cached by multible threads
     */
    volatile int mAvailablePermits;

    /**
     * A LinkedList "WaitQueue" that keeps track of the waiters in a FIFO
     */
    LinkedList<Waiter> WaitQueue;

    /**
     * For mocking only.
     */
    protected FairSemaphoreMO() {
    }

    /**
     * Constructor
     */
    public FairSemaphoreMO(int availablePermits) {
        mAvailablePermits = availablePermits;
        WaitQueue = new LinkedList<>();
    }

    /**
     * Acquire one permit from the semaphore in a manner that cannot
     * be interrupted.
     */
    @Override
    public void acquireUninterruptibly() {
        Boolean hasBeenAcquired = false;
        while(!hasBeenAcquired) {
            try {
                acquire();
                hasBeenAcquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Acquire one permit from the semaphore in a manner that can
     * be interrupted.
     */
    @Override
    public void acquire() throws InterruptedException {
        // Bail out quickly if we've been interrupted.
        if (Thread.interrupted()) {
            throw new InterruptedException();
            // Try to get a permit without blocking.
        } else if (!tryToGetPermit()) {
            // Block until a permit is available.
            waitForPermit();
        }
    }

    /**
     * Handle the case where we can get a permit without blocking.
     *
     * @return Returns true if the permit was obtained, else false.
     */
    protected boolean tryToGetPermit() {
        // Acquire intrinsic lock
        synchronized (this) {
            return tryToGetPermitUnlocked();
        }
    }

    /**
     * Factors out code that checks to see if a permit can be obtained
     * without blocking.  This method assumes the monitor lock
     * ("intrinsic lock") is held.
     *
     * @return Returns true if the permit was obtained, else false.
     */
    protected boolean tryToGetPermitUnlocked() {
        // We don't need to wait if the queue is empty and
        // permits are available.
        if(WaitQueue.isEmpty() && mAvailablePermits > 0) {
            mAvailablePermits--;
            return true;
        } else return false;
    }

    /**
     * Constructs a new Waiter (required for test mocking).
     *
     * @return A new Waiter instance
     */
    protected Waiter createWaiter() {
        return new Waiter();
    }

    /**
     * Handle the case where we need to block since there are already
     * waiters in the queue or no permits are available.
     */
    protected void waitForPermit() throws InterruptedException {
        // Call createWaiter helper method to allocate a new Waiter that
        // acts as the "specific-notification lock".
        final Waiter waiter = createWaiter();

        // Sync on waitObj intrinsic lock
        synchronized (waiter) {

            // Sync on this intrinsic lock
            synchronized (this) {
                // Add to end of queue
                if(!tryToGetPermitUnlocked()) {
                    WaitQueue.add(waiter);
                }
            }
            // Exit 'this' critical section and call 'wait()'
            try {
                while(!waiter.mReleased) {
                    waiter.wait();
                }
            } catch (Exception e) {
                synchronized (this) {
                    if(!WaitQueue.remove(waiter)) {
                        release();
                    }
                }
                throw new InterruptedException();
            }
        }
    }

    /**
     * Return one permit to the semaphore.
     */
    @Override
    public void release() {
        // Acquire this intrinsic lock
        synchronized (this) {

            // Check to see if there is an object
            // in the queue
            Waiter waitObj = WaitQueue.poll();

            if(waitObj == null) {
                // If there are no waiting threads, we can increment permit count
                mAvailablePermits++;
            } else {
                // Acquire monitor lock on waitObj
                synchronized (waitObj) {
                    // Inform the thread 'wait()'ing on waitObj that
                    // a permit is available
                    waitObj.notify();
                    waitObj.mReleased = true;
                }
                // Unlock and leave critical section
            }
        }
    }

    /**
     * @return The number of available permits.
     */
    @Override
    public int availablePermits() {
        return mAvailablePermits;
    }

    /**
     * Define a class that can be used in the "WaitQueue" to wait for
     * a specific thread to be notified.
     */
    protected static class Waiter {
        /**
         * Keeps track of whether the Waiter was released or not to
         * detected and handle "spurious wakeups".
         */
        boolean mReleased = false;

        /**
         * Constructor used for mocking.
         */
        Waiter() {
        }
    }
}
