// GraphColoring_OpenCL.c
// OpenCL implementation: GPU checks validity for many candidate colorings in parallel.
// Build requires OpenCL SDK/headers and OpenCL library.

#define CL_TARGET_OPENCL_VERSION 120
#include <CL/cl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const char* kernelSrc =
        "__kernel void check_colorings(__global const ulong* neigh, int n, __global const int* colors, int stride, __global int* out) {      \n"
        "  int gid = get_global_id(0);                                                                                                      \n"
        "  const __global int* col = colors + gid * stride;                                                                                 \n"
        "  int ok = 1;                                                                                                                      \n"
        "  for (int v=0; v<n && ok; v++) {                                                                                                  \n"
        "    int cv = col[v];                                                                                                               \n"
        "    if (cv==0) { ok=0; break; }                                                                                                    \n"
        "    ulong m = neigh[v];                                                                                                            \n"
        "    while (m) {                                                                                                                    \n"
        "      int u = (int)ctz(m);                                                                                                          \n"
        "      m &= (m-1);                                                                                                                  \n"
        "      if (u>v && col[u]==cv) { ok=0; break; }                                                                                      \n"
        "    }                                                                                                                              \n"
        "  }                                                                                                                                \n"
        "  out[gid] = ok;                                                                                                                   \n"
        "}\n";

static unsigned long long* genGraph(int n, double p, unsigned int seed) {
    if (n > 63) { printf("n must be <= 63 for bitmask graph\n"); exit(1); }
    unsigned long long* neigh = (unsigned long long*)calloc(n, sizeof(unsigned long long));
    srand(seed);
    for (int i=0;i<n;i++){
        for(int j=i+1;j<n;j++){
            double r = (double)rand() / (double)RAND_MAX;
            if (r < p) {
                neigh[i] |= (1ULL<<j);
                neigh[j] |= (1ULL<<i);
            }
        }
    }
    return neigh;
}

// very small CPU job gen: brute prefix assignments (no pruning) for demo
static int genCandidates(int n, int k, int prefixDepth, int maxCand, int* out, int stride) {
    int count = 0;
    // iterate base-k numbers for prefixDepth
    long long total = 1;
    for (int i=0;i<prefixDepth;i++) total *= k;
    for (long long t=0; t<total && count<maxCand; t++) {
        int* col = out + count*stride;
        memset(col, 0, sizeof(int)*stride);
        long long x=t;
        for (int i=0;i<prefixDepth;i++){
            col[i] = (int)(x%k) + 1;
            x/=k;
        }
        // fill the rest with 1 just so "all colored" (kernel requires nonzero)
        for (int i=prefixDepth;i<n;i++) col[i]=1;
        count++;
    }
    return count;
}

static void die(const char* msg, cl_int err){
    printf("%s (err=%d)\n", msg, err);
    exit(1);
}

int main() {
    int n=35; double p=0.25; int k=4; int prefixDepth=8;
    int stride = n;
    int maxCand = 1<<15; // 32768 candidates
    size_t candBytes = (size_t)maxCand * (size_t)stride * sizeof(int);

    unsigned long long* neigh = genGraph(n,p,42);

    int* candidates = (int*)malloc(candBytes);
    int candCount = genCandidates(n,k,prefixDepth,maxCand,candidates,stride);

    // ---- OpenCL setup ----
    cl_int err;
    cl_platform_id platform;
    cl_device_id device;
    err = clGetPlatformIDs(1, &platform, NULL); if (err) die("clGetPlatformIDs", err);
    err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, NULL); if (err) die("clGetDeviceIDs GPU", err);

    cl_context ctx = clCreateContext(NULL, 1, &device, NULL, NULL, &err); if (err) die("clCreateContext", err);
    cl_command_queue q = clCreateCommandQueue(ctx, device, 0, &err); if (err) die("clCreateCommandQueue", err);

    cl_program prog = clCreateProgramWithSource(ctx, 1, &kernelSrc, NULL, &err); if (err) die("clCreateProgramWithSource", err);
    err = clBuildProgram(prog, 1, &device, "", NULL, NULL);
    if (err) {
        char log[8192]; size_t sz=0;
        clGetProgramBuildInfo(prog, device, CL_PROGRAM_BUILD_LOG, sizeof(log), log, &sz);
        printf("Build log:\n%.*s\n", (int)sz, log);
        die("clBuildProgram", err);
    }

    cl_kernel ker = clCreateKernel(prog, "check_colorings", &err); if (err) die("clCreateKernel", err);

    cl_mem dNeigh = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, n*sizeof(unsigned long long), neigh, &err);
    if (err) die("clCreateBuffer neigh", err);

    cl_mem dColors = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, candCount*stride*sizeof(int), candidates, &err);
    if (err) die("clCreateBuffer colors", err);

    cl_mem dOut = clCreateBuffer(ctx, CL_MEM_WRITE_ONLY, candCount*sizeof(int), NULL, &err);
    if (err) die("clCreateBuffer out", err);

    err  = clSetKernelArg(ker, 0, sizeof(cl_mem), &dNeigh);
    err |= clSetKernelArg(ker, 1, sizeof(int), &n);
    err |= clSetKernelArg(ker, 2, sizeof(cl_mem), &dColors);
    err |= clSetKernelArg(ker, 3, sizeof(int), &stride);
    err |= clSetKernelArg(ker, 4, sizeof(cl_mem), &dOut);
    if (err) die("clSetKernelArg", err);

    size_t global = (size_t)candCount;
    err = clEnqueueNDRangeKernel(q, ker, 1, NULL, &global, NULL, 0, NULL, NULL);
    if (err) die("clEnqueueNDRangeKernel", err);

    int* out = (int*)malloc(candCount*sizeof(int));
    err = clEnqueueReadBuffer(q, dOut, CL_TRUE, 0, candCount*sizeof(int), out, 0, NULL, NULL);
    if (err) die("clEnqueueReadBuffer", err);

    int found = -1;
    for (int i=0;i<candCount;i++){
        if (out[i]==1) { found=i; break; }
    }

    printf("[OpenCL] candidates=%d foundIndex=%d\n", candCount, found);

    // cleanup
    free(out); free(candidates); free(neigh);
    clReleaseMemObject(dNeigh); clReleaseMemObject(dColors); clReleaseMemObject(dOut);
    clReleaseKernel(ker); clReleaseProgram(prog);
    clReleaseCommandQueue(q); clReleaseContext(ctx);
    return 0;
}
