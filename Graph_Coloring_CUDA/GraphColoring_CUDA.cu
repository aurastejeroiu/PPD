// GraphColoring_CUDA.cu
// CUDA implementation: GPU checks validity for many candidate colorings in parallel.
// Works best for n <= 63 (bitmask graph). Simple and robust for coursework.

#include <cuda_runtime.h>
#include <device_launch_parameters.h>

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <random>
#include <algorithm>

static void cudaCheck(cudaError_t e, const char* msg) {
    if (e != cudaSuccess) {
        std::fprintf(stderr, "CUDA error: %s : %s\n", msg, cudaGetErrorString(e));
        std::exit(1);
    }
}

// neigh[v] = bitmask of neighbors (n<=63)
__global__ void checkColoringsKernel(const unsigned long long* neigh, int n,
                                     const int* colors, int stride,
                                     int* outOk) {
    int gid = (int)(blockIdx.x * blockDim.x + threadIdx.x);
    // outOk size = numCandidates; caller launches exact grid so gid < numCandidates
    const int* col = colors + gid * stride;

    int ok = 1;
    for (int v = 0; v < n && ok; v++) {
        int cv = col[v];
        if (cv == 0) { ok = 0; break; } // require full coloring in this demo
        unsigned long long m = neigh[v];
        while (m) {
            int u = __ffsll((long long)m) - 1; // index of lowest set bit
            m &= (m - 1);
            if (u > v && col[u] == cv) { ok = 0; break; }
        }
    }
    outOk[gid] = ok;
}

static std::vector<unsigned long long> randomGraphBitmask(int n, double p, unsigned int seed) {
    if (n > 63) {
        std::fprintf(stderr, "This CUDA demo uses bitmasks => n must be <= 63.\n");
        std::exit(1);
    }
    std::mt19937 rng(seed);
    std::uniform_real_distribution<double> dist(0.0, 1.0);

    std::vector<unsigned long long> neigh(n, 0ULL);
    for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
            if (dist(rng) < p) {
                neigh[i] |= (1ULL << j);
                neigh[j] |= (1ULL << i);
            }
        }
    }
    return neigh;
}

// Generate candidates by enumerating base-k prefixes of length prefixDepth.
// Then fill remaining vertices with 1 so kernel checks a *complete* coloring.
// (Coursework-friendly: GPU part is the parallel validity check.)
static int generateCandidates(int n, int k, int prefixDepth, int maxCand, std::vector<int>& out) {
    const int stride = n;
    out.assign((size_t)maxCand * (size_t)stride, 0);

    long long total = 1;
    for (int i = 0; i < prefixDepth; i++) total *= k;

    int count = 0;
    for (long long t = 0; t < total && count < maxCand; t++) {
        int* col = out.data() + (size_t)count * (size_t)stride;
        // prefix = digits in base k
        long long x = t;
        for (int i = 0; i < prefixDepth; i++) {
            col[i] = (int)(x % k) + 1;
            x /= k;
        }
        for (int i = prefixDepth; i < n; i++) col[i] = 1; // complete coloring for demo
        count++;
    }
    return count;
}

int main(int argc, char** argv) {
    // You can tweak these from CLion Run args if you want:
    // n p k seed prefixDepth maxCand
    int n = (argc > 1) ? std::atoi(argv[1]) : 35;
    double p = (argc > 2) ? std::atof(argv[2]) : 0.25;
    int k = (argc > 3) ? std::atoi(argv[3]) : 4;
    unsigned int seed = (argc > 4) ? (unsigned int)std::atoi(argv[4]) : 42;
    int prefixDepth = (argc > 5) ? std::atoi(argv[5]) : 8;
    int maxCand = (argc > 6) ? std::atoi(argv[6]) : (1 << 15); // 32768

    std::printf("[CUDA] n=%d p=%.3f k=%d seed=%u prefixDepth=%d maxCand=%d\n",
                n, p, k, seed, prefixDepth, maxCand);

    // Device info quick sanity
    int devCount = 0;
    cudaCheck(cudaGetDeviceCount(&devCount), "cudaGetDeviceCount");
    if (devCount == 0) {
        std::fprintf(stderr, "No CUDA devices found.\n");
        return 1;
    }

    auto neighHost = randomGraphBitmask(n, p, seed);

    // Generate candidates on CPU
    std::vector<int> candHost;
    int candCount = generateCandidates(n, k, prefixDepth, maxCand, candHost);
    const int stride = n;

    std::printf("[CUDA] generated candidates=%d\n", candCount);

    // Allocate GPU memory
    unsigned long long* dNeigh = nullptr;
    int* dColors = nullptr;
    int* dOut = nullptr;

    cudaCheck(cudaMalloc((void**)&dNeigh, (size_t)n * sizeof(unsigned long long)), "cudaMalloc dNeigh");
    cudaCheck(cudaMalloc((void**)&dColors, (size_t)candCount * (size_t)stride * sizeof(int)), "cudaMalloc dColors");
    cudaCheck(cudaMalloc((void**)&dOut, (size_t)candCount * sizeof(int)), "cudaMalloc dOut");

    cudaCheck(cudaMemcpy(dNeigh, neighHost.data(), (size_t)n * sizeof(unsigned long long), cudaMemcpyHostToDevice),
              "cudaMemcpy neigh");
    cudaCheck(cudaMemcpy(dColors, candHost.data(), (size_t)candCount * (size_t)stride * sizeof(int), cudaMemcpyHostToDevice),
              "cudaMemcpy colors");

    // Launch kernel
    int block = 256;
    int grid = (candCount + block - 1) / block;

    // We need to ensure threads with gid>=candCount don't read out of bounds.
    // Easiest: allocate outOk of size candCount and just launch exact grid, but guard:
    // We'll add a simple guard by making candCount multiple of block via logic:
    // For simplicity here, we just add guard by re-launching with an if inside kernel is absent,
    // so we ensure grid covers candCount exactly but gid may exceed. We'll handle by rounding up and
    // ensuring host buffers are big enough only for candCount. So add guard in kernel? Let's do it safely:
    // (Kernel above doesn't guard, so we must guard here by launching only candCount threads.)
    // CUDA doesn't support partial block launch; better is to add guard. We'll do it by a second kernel with guard:
    // To keep single file simple, we re-define a guarded kernel inline via lambda? Not possible.
    // Instead: enforce grid so that gid < candCount always by making candCount divisible by block:
    int padded = ((candCount + block - 1) / block) * block;
    if (padded != candCount) {
        // pad host candidates & resize device buffers safely
        candHost.resize((size_t)padded * (size_t)stride, 1);
        for (int i = candCount; i < padded; i++) {
            int* col = candHost.data() + (size_t)i * (size_t)stride;
            std::fill(col, col + stride, 1);
        }
        // re-copy expanded
        cudaCheck(cudaFree(dColors), "cudaFree dColors");
        cudaCheck(cudaFree(dOut), "cudaFree dOut");

        candCount = padded;
        grid = (candCount + block - 1) / block;

        cudaCheck(cudaMalloc((void**)&dColors, (size_t)candCount * (size_t)stride * sizeof(int)), "cudaMalloc dColors (pad)");
        cudaCheck(cudaMalloc((void**)&dOut, (size_t)candCount * sizeof(int)), "cudaMalloc dOut (pad)");
        cudaCheck(cudaMemcpy(dColors, candHost.data(), (size_t)candCount * (size_t)stride * sizeof(int), cudaMemcpyHostToDevice),
                  "cudaMemcpy colors (pad)");
    }

    // time (rough)
    cudaEvent_t start, stop;
    cudaCheck(cudaEventCreate(&start), "cudaEventCreate start");
    cudaCheck(cudaEventCreate(&stop), "cudaEventCreate stop");
    cudaCheck(cudaEventRecord(start), "cudaEventRecord start");

    checkColoringsKernel<<<grid, block>>>(dNeigh, n, dColors, stride, dOut);
    cudaCheck(cudaGetLastError(), "kernel launch");
    cudaCheck(cudaDeviceSynchronize(), "cudaDeviceSynchronize");

    cudaCheck(cudaEventRecord(stop), "cudaEventRecord stop");
    cudaCheck(cudaEventSynchronize(stop), "cudaEventSynchronize stop");
    float ms = 0.f;
    cudaCheck(cudaEventElapsedTime(&ms, start, stop), "cudaEventElapsedTime");

    // Copy results back
    std::vector<int> outHost((size_t)candCount, 0);
    cudaCheck(cudaMemcpy(outHost.data(), dOut, (size_t)candCount * sizeof(int), cudaMemcpyDeviceToHost),
              "cudaMemcpy out");

    int found = -1;
    for (int i = 0; i < candCount; i++) {
        if (outHost[i] == 1) { found = i; break; }
    }

    std::printf("[CUDA] time(ms)=%.3f foundIndex=%d\n", ms, found);
    if (found >= 0) {
        const int* sol = candHost.data() + (size_t)found * (size_t)stride;
        std::printf("[CUDA] coloring: [");
        for (int i = 0; i < n; i++) {
            std::printf("%d%s", sol[i], (i + 1 == n) ? "" : ", ");
        }
        std::printf("]\n");
    }

    // cleanup
    cudaEventDestroy(start);
    cudaEventDestroy(stop);
    cudaFree(dNeigh);
    cudaFree(dColors);
    cudaFree(dOut);

    return 0;
}
