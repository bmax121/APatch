// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for string escapes in dtc
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

#define CHUNKSIZE	1024

static char *load_file(const char *name, int *len)
{
	FILE *f;
	char *buf = NULL;
	int bufsize = 0, n;

	*len = 0;

	f = fopen(name, "r");
	if (!f)
		FAIL("Couldn't open \"%s\": %s", name, strerror(errno));

	while (!feof(f)) {
		if (bufsize < (*len + CHUNKSIZE)) {
			buf = xrealloc(buf, *len + CHUNKSIZE);
			bufsize = *len + CHUNKSIZE;
		}

		n = fread(buf + *len, 1, CHUNKSIZE, f);
		if (ferror(f))
			FAIL("Error reading \"%s\": %s", name, strerror(errno));
		*len += n;
	}

	return buf;
}

int main(int argc, char *argv[])
{
	void *fdt;
	char *incbin;
	int len;

	test_init(argc, argv);

	if (argc != 3)
		CONFIG("Usage: %s <incbin file> <dtb file>", argv[0]);

	incbin = load_file(argv[1], &len);
	fdt = load_blob(argv[2]);

	check_getprop(fdt, 0, "incbin", len, incbin);
	check_getprop(fdt, 0, "incbin-partial", 17, incbin + 13);

	PASS();
}
