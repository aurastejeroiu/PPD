package com.pdp;

import mpi.MPI;

import java.util.*;

public class Main {

    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        // Variables
        List<String> vars = List.of("A", "B", "C");

        // Static subscriptions (IMPORTANT: rank 0 = sequencer trebuie să fie subscriber la toate)
        // Exemplu:
        // - A: {0,2}
        // - B: {0,1,2}
        // - C: {0,3}
        Map<String, Set<Integer>> subs = new HashMap<>();
        subs.put("A", new HashSet<>(Arrays.asList(0, 2)));
        subs.put("B", new HashSet<>(Arrays.asList(0, 1, 2)));
        subs.put("C", new HashSet<>(Arrays.asList(0, 3)));

        Dsm dsm = new Dsm(vars, subs, (seq, varName, newValue) ->
                System.out.printf("[rank %d] callback: seq=%d %s=%d%n", rank, seq, varName, newValue)
        );

        MPI.COMM_WORLD.Barrier();

        // Demo operations (doar subscriberii modifică)
        if (rank == 0) {
            dsm.write("A", 10);  // ok (0 e subscriber la A)
            dsm.write("C", 1);   // ok (0 e subscriber la C)
        }

        if (rank == 1) {
            dsm.write("B", 20);  // ok (1 e subscriber la B)
        }

        if (rank == 2) {
            boolean okA = dsm.compareAndExchange("A", 10, 15); // ok (2 e subscriber la A)
            System.out.printf("[rank %d] CAS(A,10->15) success=%s%n", rank, okA);

            boolean okB = dsm.compareAndExchange("B", 25, 30); // ok (2 e subscriber la B)
            System.out.printf("[rank %d] CAS(B,25->30) success=%s%n", rank, okB);
        }

        // Let messages flow
        dsm.drainBestEffort(300);
        System.out.println("[rank " + rank + "] A=" + dsm.getLocalValue("A")
                + " B=" + dsm.getLocalValue("B")
                + " C=" + dsm.getLocalValue("C"));


        MPI.COMM_WORLD.Barrier();

        // Stop logic
        if (rank == 0) {
            System.out.printf("[rank %d] Final local values: A=%d B=%d C=%d%n",
                    rank, dsm.getLocalValue("A"), dsm.getLocalValue("B"), dsm.getLocalValue("C"));
            dsm.broadcastStop();
        } else {
            // wait until STOP arrives
            while (!dsm.pollStop()) {
                dsm.progress();
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }
            // consume STOP message
            DsmMessage[] buf = new DsmMessage[1];
            MPI.COMM_WORLD.Recv(buf, 0, 1, MPI.OBJECT, MPI.ANY_SOURCE, 12);
        }

        MPI.Finalize();
    }
}
