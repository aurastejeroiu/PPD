package com.pdp;

import mpi.MPI;

/**
 * Distributed Karatsuba using MPI.
 *
 * We distribute ONLY the top-level 3 Karatsuba multiplications:
 *   z0 = p1*q1
 *   z1 = (p1+p2)*(q1+q2)
 *   z2 = p2*q2
 *
 * Root (rank 0) sends these 3 tasks to ranks 1..3 (if available).
 * Workers compute them using the same sequential Karatsuba from Lab 5
 * and send results back.
 */
public final class MpiKaratsubaMultiplier {
    private static final int TAG_TASK = 10;
    private static final int TAG_RESULT = 11;

    private MpiKaratsubaMultiplier() {}

    public static Polynomial multiply(Polynomial p, Polynomial q) {
        int rank = MPI.COMM_WORLD.Rank();
        int worldSize = MPI.COMM_WORLD.Size();

        // Degenerate case
        if (rank == 0 && worldSize == 1) {
            return SequentialAlgorithms.karatsubaSequential(p, q);
        }

        // Base case: small => compute locally (root), nothing to distribute.
        if (rank == 0 && (p.degree() < 3 || q.degree() < 3)) {
            return p.multiply(q);
        }

        if (rank != 0) {
            // Only ranks 1..3 can receive tasks (if they exist).
            if (rank >= 1 && rank <= 3) {
                return workerComputeOnce(rank);
            }
            return null;
        }

        // Root: split
        int split = Math.max(p.degree(), q.degree()) / 2 + 1;
        Polynomial p1 = new Polynomial(p.powers.subList(0, split));
        Polynomial p2 = new Polynomial(p.powers.subList(split, p.powers.size()));
        Polynomial q1 = new Polynomial(q.powers.subList(0, split));
        Polynomial q2 = new Polynomial(q.powers.subList(split, q.powers.size()));

        Polynomial z0 = null;
        Polynomial z1 = null;
        Polynomial z2 = null;

        // Dispatch tasks if workers exist.
        // rank 1 -> z0, rank 2 -> z1, rank 3 -> z2
        if (worldSize > 1) {
            sendTask(1, p1, q1);
        } else {
            z0 = SequentialAlgorithms.karatsubaSequential(p1, q1);
        }

        if (worldSize > 2) {
            sendTask(2, p1.add(p2), q1.add(q2));
        } else {
            z1 = SequentialAlgorithms.karatsubaSequential(p1.add(p2), q1.add(q2));
        }

        if (worldSize > 3) {
            sendTask(3, p2, q2);
        } else {
            z2 = SequentialAlgorithms.karatsubaSequential(p2, q2);
        }

        // Receive results for dispatched tasks
        if (worldSize > 1) z0 = recvResult(1);
        if (worldSize > 2) z1 = recvResult(2);
        if (worldSize > 3) z2 = recvResult(3);

        // Combine
        return z2.toPower(2 * split)
                .add(z1.subtract(z2).subtract(z0).toPower(split))
                .add(z0);
    }

    private static void sendTask(int destRank, Polynomial p, Polynomial q) {
        if (destRank >= MPI.COMM_WORLD.Size()) return;

        int[] pArr = PolyUtils.toIntArray(p);
        int[] qArr = PolyUtils.toIntArray(q);

        int[] sizes = new int[]{pArr.length, qArr.length};
        MPI.COMM_WORLD.Send(sizes, 0, 2, MPI.INT, destRank, TAG_TASK);
        MPI.COMM_WORLD.Send(pArr, 0, pArr.length, MPI.INT, destRank, TAG_TASK);
        MPI.COMM_WORLD.Send(qArr, 0, qArr.length, MPI.INT, destRank, TAG_TASK);
    }

    private static Polynomial recvResult(int srcRank) {
        int[] lenArr = new int[1];
        MPI.COMM_WORLD.Recv(lenArr, 0, 1, MPI.INT, srcRank, TAG_RESULT);
        int len = lenArr[0];
        int[] coeffs = new int[len];
        MPI.COMM_WORLD.Recv(coeffs, 0, len, MPI.INT, srcRank, TAG_RESULT);
        return PolyUtils.fromIntArray(coeffs);
    }

    private static Polynomial workerComputeOnce(int rank) {
        // Receive sizes
        int[] sizes = new int[2];
        MPI.COMM_WORLD.Recv(sizes, 0, 2, MPI.INT, 0, TAG_TASK);
        int n = sizes[0];
        int m = sizes[1];

        int[] pArr = new int[n];
        int[] qArr = new int[m];
        MPI.COMM_WORLD.Recv(pArr, 0, n, MPI.INT, 0, TAG_TASK);
        MPI.COMM_WORLD.Recv(qArr, 0, m, MPI.INT, 0, TAG_TASK);

        Polynomial p = PolyUtils.fromIntArray(pArr);
        Polynomial q = PolyUtils.fromIntArray(qArr);

        Polynomial result = SequentialAlgorithms.karatsubaSequential(p, q);

        int[] resArr = PolyUtils.toIntArray(result);
        int[] lenArr = new int[]{resArr.length};
        MPI.COMM_WORLD.Send(lenArr, 0, 1, MPI.INT, 0, TAG_RESULT);
        MPI.COMM_WORLD.Send(resArr, 0, resArr.length, MPI.INT, 0, TAG_RESULT);

        return null;
    }
}
