package com.pdp;

import mpi.MPI;

/**
 * Distributed regular multiplication (O(n^2)) using MPI.
 *
 * Strategy:
 * - Broadcast both polynomials to all ranks.
 * - Split the result coefficient indices among ranks.
 * - Each rank computes its chunk of result coefficients.
 * - Root gathers all chunks with Gatherv.
 */
public final class MpiRegularMultiplier {

    private MpiRegularMultiplier() {}

    public static Polynomial multiply(Polynomial p, Polynomial q) {
        int rank = MPI.COMM_WORLD.Rank();
        int worldSize = MPI.COMM_WORLD.Size();

        // Broadcast sizes first
        int[] sizes = new int[2];
        if (rank == 0) {
            sizes[0] = p.powers.size();
            sizes[1] = q.powers.size();
        }
        MPI.COMM_WORLD.Bcast(sizes, 0, 2, MPI.INT, 0);

        int n = sizes[0];
        int m = sizes[1];

        // Broadcast coefficients
        int[] a = new int[n];
        int[] b = new int[m];
        if (rank == 0) {
            a = PolyUtils.toIntArray(p);
            b = PolyUtils.toIntArray(q);
        }
        MPI.COMM_WORLD.Bcast(a, 0, n, MPI.INT, 0);
        MPI.COMM_WORLD.Bcast(b, 0, m, MPI.INT, 0);

        int resultLen = n + m - 1;

        int chunk = (resultLen + worldSize - 1) / worldSize;
        int start = rank * chunk;
        int end = Math.min(resultLen, start + chunk);
        int localLen = Math.max(0, end - start);

        int[] local = new int[localLen];

        for (int k = start; k < end; k++) {
            long sum = 0;
            int iMin = Math.max(0, k - (m - 1));
            int iMax = Math.min(n - 1, k);
            for (int i = iMin; i <= iMax; i++) {
                sum += (long) a[i] * (long) b[k - i];
            }
            local[k - start] = (int) sum;
        }

        // Gather
        int[] result = null;
        int[] recvCounts = null;
        int[] displs = null;

        if (rank == 0) {
            result = new int[resultLen];
            recvCounts = new int[worldSize];
            displs = new int[worldSize];
            for (int r = 0; r < worldSize; r++) {
                int s = r * chunk;
                int e = Math.min(resultLen, s + chunk);
                recvCounts[r] = Math.max(0, e - s);
                displs[r] = s;
            }
        }

        MPI.COMM_WORLD.Gatherv(
                local, 0, localLen, MPI.INT,
                result, 0, recvCounts, displs, MPI.INT,
                0
        );

        return (rank == 0) ? PolyUtils.fromIntArray(result) : null;
    }
}
