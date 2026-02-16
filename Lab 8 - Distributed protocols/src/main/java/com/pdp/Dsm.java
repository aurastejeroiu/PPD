package com.pdp;

import mpi.MPI;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Dsm {

    // MPI tags
    private static final int TAG_REQUEST = 10; // subscriber -> sequencer
    private static final int TAG_ORDER   = 11; // sequencer  -> subscribers(var)
    private static final int TAG_STOP    = 12; // sequencer  -> everyone (control)

    private final int rank;
    private final int size;

    // We keep sequencer fixed to rank 0 (but it must be subscriber to vars it sequences).
    private final int sequencerRank = 0;

    // Static subscriptions
    private final Map<String, Set<Integer>> subscribers;

    // Local replica of values (only meaningful for variables this process subscribes to)
    private final Map<String, Integer> localValues = new ConcurrentHashMap<>();

    // Callback to main
    private final DsmChangeListener listener;

    // Sequencer state
    private long globalSeq = 0;

    // Request id generator per process
    private final AtomicLong reqIdGen = new AtomicLong(1);

    // CAS results awaited by origin
    private final Map<Long, Boolean> casResults = new ConcurrentHashMap<>();

    public Dsm(List<String> variables,
               Map<String, Set<Integer>> subscribers,
               DsmChangeListener listener) {

        this.rank = MPI.COMM_WORLD.Rank();
        this.size = MPI.COMM_WORLD.Size();
        this.subscribers = new HashMap<>(subscribers);
        this.listener = listener;

        // init values for all vars (0), but only subscriber processes will actually receive updates
        for (String v : variables) {
            localValues.put(v, 0);
        }
    }

    // ======= PUBLIC API =======

    public void write(String var, int value) {
        ensureSubscriber(var);

        long rid = reqIdGen.getAndIncrement();
        DsmMessage req = DsmMessage.writeRequest(var, value, rank, rid);

        // request goes only to sequencer (which is also a subscriber to the variable in the intended setup)
        MPI.COMM_WORLD.Send(new DsmMessage[]{req}, 0, 1, MPI.OBJECT, sequencerRank, TAG_REQUEST);
    }

    /**
     * Compare-and-exchange. Returns true if it changed the value, false otherwise.
     * This call blocks until the ordered CAS message is delivered back to the origin.
     */
    public boolean compareAndExchange(String var, int expected, int newValue) {
        ensureSubscriber(var);

        long rid = reqIdGen.getAndIncrement();
        DsmMessage req = DsmMessage.casRequest(var, expected, newValue, rank, rid);

        MPI.COMM_WORLD.Send(new DsmMessage[]{req}, 0, 1, MPI.OBJECT, sequencerRank, TAG_REQUEST);

        // Wait until the ordered CAS comes back and we compute/receive its result.
        while (!casResults.containsKey(rid)) {
            progress();
            // small yield to avoid burning CPU
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }

        return casResults.remove(rid);
    }

    public int getLocalValue(String var) {
        return localValues.getOrDefault(var, 0);
    }

    public void drainBestEffort(int millis) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            progress();
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }

    public boolean pollStop() {
        return MPI.COMM_WORLD.Iprobe(MPI.ANY_SOURCE, TAG_STOP) != null;
    }

    public void broadcastStop() {
        if (rank != sequencerRank) return;
        DsmMessage stop = DsmMessage.stop();
        for (int r = 0; r < size; r++) {
            MPI.COMM_WORLD.Send(new DsmMessage[]{stop}, 0, 1, MPI.OBJECT, r, TAG_STOP);
        }
    }

    /**
     * Called frequently from main.
     * - Sequencer: receives requests, assigns global order, sends order only to subscribers(var).
     * - Everyone: receives ordered updates (from sequencer) and applies them immediately
     *   (MPI guarantees message order from same source to same dest with same tag).
     */
    public void progress() {

        // 1) Sequencer handles requests
        if (rank == sequencerRank) {
            while (MPI.COMM_WORLD.Iprobe(MPI.ANY_SOURCE, TAG_REQUEST) != null) {
                DsmMessage[] buf = new DsmMessage[1];
                MPI.COMM_WORLD.Recv(buf, 0, 1, MPI.OBJECT, MPI.ANY_SOURCE, TAG_REQUEST);
                handleRequestAsSequencer(buf[0]);
            }
        }

        // 2) Everyone handles ordered messages (only sent by sequencer to subscribers of that var)
        while (MPI.COMM_WORLD.Iprobe(sequencerRank, TAG_ORDER) != null) {
            DsmMessage[] buf = new DsmMessage[1];
            MPI.COMM_WORLD.Recv(buf, 0, 1, MPI.OBJECT, sequencerRank, TAG_ORDER);
            applyOrdered(buf[0]);
        }

        // 3) STOP messages are handled by main (pollStop + Recv if you want),
        // but we can also clear them here if desired (not required).
    }

    // ======= INTERNALS =======

    private void handleRequestAsSequencer(DsmMessage req) {
        // Enforce rule: only subscribers can modify
        Set<Integer> subs = subscribers.get(req.var);
        if (subs == null || !subs.contains(req.originRank)) {
            // Ignore illegal request (or you can throw, but throwing in MPI rank 0 is ugly).
            return;
        }

        long seq = ++globalSeq;

        if (req.kind == DsmMessage.Kind.REQUEST_WRITE) {
            DsmMessage order = DsmMessage.orderFromWrite(seq, req);
            // send only to subscribers of that variable
            multicastToSubscribers(req.var, order);

            // update sequencer local replica (since sequencer is a subscriber in the intended setup)
            localValues.put(req.var, req.value);

        } else if (req.kind == DsmMessage.Kind.REQUEST_CAS) {

            // decide CAS based on current local replica at sequencer
            // (sequencer is subscriber -> its value is consistent in the global order)
            int cur = localValues.getOrDefault(req.var, 0);
            boolean success = (cur == req.expected);
            if (success) {
                localValues.put(req.var, req.newValue);
            }

            DsmMessage order = DsmMessage.orderFromCas(seq, req, success);
            multicastToSubscribers(req.var, order);
        }
    }

    private void multicastToSubscribers(String var, DsmMessage order) {
        Set<Integer> subs = subscribers.get(var);
        if (subs == null) return;

        // Messages go ONLY between subscribers of that variable.
        // Note: sequencer is sending, but it must be included in subs by design.
        for (int dest : subs) {
            MPI.COMM_WORLD.Send(new DsmMessage[]{order}, 0, 1, MPI.OBJECT, dest, TAG_ORDER);
        }
    }

    private void applyOrdered(DsmMessage msg) {
        if (msg.kind != DsmMessage.Kind.ORDER_APPLY) return;

        // Apply write or cas
        boolean changed = false;

        if (msg.op == DsmMessage.Op.WRITE) {
            localValues.put(msg.var, msg.value);
            changed = true;

        } else if (msg.op == DsmMessage.Op.CAS) {
            // Sequencer already decided casSuccess; everyone applies accordingly.
            if (msg.casSuccess) {
                localValues.put(msg.var, msg.newValue);
                changed = true;
            }
            // origin learns result (even if not changed)
            if (msg.originRank == rank) {
                casResults.put(msg.requestId, msg.casSuccess);
            }
        }

        // Callback ONLY when the variable actually changed
        if (changed && listener != null) {
            listener.onChange(msg.seq, msg.var, localValues.getOrDefault(msg.var, 0));
        }
    }

    private void ensureSubscriber(String var) {
        Set<Integer> subs = subscribers.get(var);
        if (subs == null || !subs.contains(rank)) {
            throw new IllegalStateException("Rank " + rank + " is NOT subscribed to variable " + var +
                    " and is not allowed to modify it.");
        }
    }
}
