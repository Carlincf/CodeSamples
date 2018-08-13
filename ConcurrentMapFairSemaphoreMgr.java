package edu.vandy.simulator.managers.palantiri.concurrentMapFairSemaphore;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.vandy.simulator.managers.palantiri.Palantir;
import edu.vandy.simulator.managers.palantiri.PalantiriManager;

/**
 * Defines a mechanism that mediates concurrent access to a fixed
 * number of available Palantiri.  This class uses a "fair" Semaphore
 * and a ConcurrentHashMap to mediate concurrent access to the
 * Palantiri.
 */
public class ConcurrentMapFairSemaphoreMgr
        extends PalantiriManager {
    /**
     * Debugging tag used by the Android logger.
     */
    protected final static String TAG =
            ConcurrentMapFairSemaphoreMgr.class.getSimpleName();

    /**
     * A FairSemaphore that limits concurrent access to the fixed
     * number of available palantiri managed by the PalantiriManager.
     */
    private FairSemaphore mAvailablePalantiri;

    /**
     * A concurrent hashmap that associates the Palantiri key to
     * the boolean values that keep track of whether the key is
     * available.
     */
    private ConcurrentMap<Palantir, Boolean> mPalantiriMap;

    /**
     * Zero parameter constructor required for Factory creation.
     */
    public ConcurrentMapFairSemaphoreMgr() {
    }

    /**
     * Resets the fields to their initial values and tells all beings
     * to reset themselves.
     */
    @Override
    public void reset() {
        super.reset();
    }

    /**
     * Called by super class to build the Palantiri model.
     */
    @Override
    public void buildModel() {
        // Create a new ConcurrentHashMap 
        mPalantiriMap = new ConcurrentHashMap<>();
      
        // Iterate through the List of Palantiri and initialize each key 
        // in the HashMap with "true" to indicate it's available
        getPalantiri().forEach(palantir -> mPalantiriMap.put(palantir,true));
      
        // Initialize the fair Semaphore that mediates
        // concurrent access to the given Palantiri.
        mAvailablePalantiri = new FairSemaphoreMO(mPalantiriMap.size());
    }

    /**
     * Get a Palantir from the PalantiriManager, blocking until one is
     * available.
     */
    @Override
    @NotNull
    public Palantir acquire() throws CancellationException {
      
        // Catches exceptions
        try {
            // If this succeeds, there should always be a
            // palantir available in the Hashmap
            mAvailablePalantiri.acquireUninterruptibly();

            // However, we may not find that palantir on our first
            // pass through the Map. So instead we need to pass
            // through multiple times until we find an available
            // palantir
            Iterator<Map.Entry<Palantir,Boolean>> iter =
                    mPalantiriMap.entrySet().iterator();

            // We'll use this loop to repeatedly check for an
            // available Palantir, even if we don't find one
            // on our first pass through the Map
            while(true) {
                // "Left-to-right" pass through Map
                while(iter.hasNext()) {
                    Map.Entry<Palantir,Boolean> entry = iter.next();
                    // If we find an available palantir...
                    if(entry.getValue()) {
                        // Atomically replace the value of the key with "false"
                        Palantir palantir = entry.getKey();
                        if(mPalantiriMap.replace(palantir,true,false)) {
                            // The replacement was a success!
                            return palantir;
                        } // If we reach this point, the replace call failed so we try again
                    }
                }
                // Otherwise, if we reach the end of the pass through
                // without having found an available Palantir, we need to
                // try again and that means resetting the iterator.
                iter = mPalantiriMap.entrySet().iterator();
            }
        } catch (Exception e) {
            throw new CancellationException();
        }
    }

    /**
     * Returns the designated @code palantir to the PalantiriManager
     * so that it's available for other Threads to use.  An invalid @a
     * palantir is ignored.
     */
    @Override
    public void release(final Palantir palantir) {
        // Put the "true" value back into ConcurrentHashMap for the
        // palantir key and release the Semaphore if all works properly
        if(palantir != null) {
            boolean hasPal = false;
            try {
                if(!mPalantiriMap.put(palantir,true))
                    hasPal = true;
            } finally {
                if (hasPal)
                    mAvailablePalantiri.release();
            }
        }
    }

    /**
     * Returns the number of available permits on the semaphore.
     */
    protected int availablePermits() {
        return mAvailablePalantiri.availablePermits();
    }

    /**
     * Called when the simulation is being shutdown to allow model
     * components the opportunity to and release resources and to
     * reset field values.  The Beings will have already have been
     * shutdown by the base class before calling this method.
     */
    @Override
    public void shutdownNow() {
        Log.d(TAG, "shutdownNow: called.");
    }
}
