package com.pdp;

import mpi.MPI;

public class Main {

    private static final int DEFAULT_POLY_SIZE = 2048;
    private static final int DEFAULT_MAX_COEFF = 50;

    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int worldSize = MPI.COMM_WORLD.Size();

        int[] params = parseLastTwoInts(args, DEFAULT_POLY_SIZE, DEFAULT_MAX_COEFF);
        int polySize = params[0];
        int maxCoeff = params[1];

        // safety
        if (polySize <= 0) polySize = DEFAULT_POLY_SIZE;
        if (maxCoeff <= 0) maxCoeff = DEFAULT_MAX_COEFF;

        if (rank == 0) {
            System.out.println("World size: " + worldSize);
            System.out.println("Poly size: " + polySize + " coefficients");
            System.out.println("Max coeff (exclusive): " + maxCoeff);
        }

        // Root generates the polynomials. Other ranks can keep null (MPI methods handle this).
        Polynomial p = null;
        Polynomial q = null;

        if (rank == 0) {
            p = PolyUtils.randomPolynomial(polySize, maxCoeff);
            q = PolyUtils.randomPolynomial(polySize, maxCoeff);
        }

        // ===== CPU baseline on rank 0 (Lab 5 style) =====
        Polynomial cpuReg = null;
        Polynomial cpuKar = null;

        if (rank == 0) {
            long t0 = System.nanoTime();
            cpuReg = SequentialAlgorithms.regularSequential(p, q);
            long t1 = System.nanoTime();
            System.out.println("CPU Regular:   " + ((t1 - t0) / 1_000_000.0) + " ms");

            long t2 = System.nanoTime();
            cpuKar = SequentialAlgorithms.karatsubaSequential(p, q);
            long t3 = System.nanoTime();
            System.out.println("CPU Karatsuba: " + ((t3 - t2) / 1_000_000.0) + " ms");
        }

        // ===== MPI Regular (broadcast+gather inside) =====
        long t4 = System.nanoTime();
        Polynomial mpiReg = MpiRegularMultiplier.multiply(p, q);
        long t5 = System.nanoTime();

        if (rank == 0) {
            System.out.println("MPI Regular:   " + ((t5 - t4) / 1_000_000.0) + " ms");
            System.out.println("MPI Regular correct vs CPU Regular: " + PolyUtils.equalsPoly(mpiReg, cpuReg));
        }

        // ===== MPI Karatsuba (workers compute tasks inside) =====
        long t6 = System.nanoTime();
        Polynomial mpiKar = MpiKaratsubaMultiplier.multiply(p, q);
        long t7 = System.nanoTime();

        if (rank == 0) {
            System.out.println("MPI Karatsuba: " + ((t7 - t6) / 1_000_000.0) + " ms");
            System.out.println("MPI Karatsuba correct vs CPU Karatsuba: " + PolyUtils.equalsPoly(mpiKar, cpuKar));
        }

        MPI.Finalize();
    }

    /**
     * MPJ may inject args. We take the LAST 2 integers found in args:
     *   ... 2048 50
     * Returns [polySize, maxCoeff]
     */
    private static int[] parseLastTwoInts(String[] args, int defSize, int defMax) {
        Integer last = null;
        Integer secondLast = null;

        for (int i = args.length - 1; i >= 0; i--) {
            Integer v = tryParseInt(args[i]);
            if (v == null) continue;

            if (last == null) last = v;
            else {
                secondLast = v;
                break;
            }
        }

        int polySize = (secondLast != null) ? secondLast : defSize;
        int maxCoeff = (last != null) ? last : defMax;
        return new int[]{polySize, maxCoeff};
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception ignored) { return null; }
    }
}
