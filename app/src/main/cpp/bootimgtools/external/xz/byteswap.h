#if defined(__APPLE__)
#include <libkern/OSByteOrder.h>

/* We assume little endian. */
#define htobe64(x) OSSwapHostToBigInt64(x)
#define htobe32(x) OSSwapHostToBigInt32(x)
#define htobe16(x) OSSwapHostToBigInt16(x)

#define be64toh(x) OSSwapBigToHostInt64(x)
#define be32toh(x) OSSwapBigToHostInt32(x)
#define be16toh(x) OSSwapBigToHostInt16(x)

#else

#include <byteswap.h>

#endif

