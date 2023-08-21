// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Basic testcase for read-only access
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

int main(int argc, char *argv[])
{
	void *fdt, *fdt1, *fdt2, *fdt3;
	char *buf;
	int shuntsize;
	int bufsize;
	int err;
	const char *inname;
	char outname[PATH_MAX];

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);
	inname = argv[1];

	shuntsize = ALIGN(fdt_totalsize(fdt) / 2, sizeof(uint64_t));
	bufsize = fdt_totalsize(fdt) + shuntsize;
	buf = xmalloc(bufsize);

	fdt1 = buf;
	err = fdt_move(fdt, fdt1, bufsize);
	if (err)
		FAIL("Failed to move tree into new buffer: %s",
		     fdt_strerror(err));
	sprintf(outname, "moved.%s", inname);
	save_blob(outname, fdt1);

	fdt2 = buf + shuntsize;
	err = fdt_move(fdt1, fdt2, bufsize-shuntsize);
	if (err)
		FAIL("Failed to shunt tree %d bytes: %s",
		     shuntsize, fdt_strerror(err));
	sprintf(outname, "shunted.%s", inname);
	save_blob(outname, fdt2);

	fdt3 = buf;
	err = fdt_move(fdt2, fdt3, bufsize);
	if (err)
		FAIL("Failed to deshunt tree %d bytes: %s",
		     shuntsize, fdt_strerror(err));
	sprintf(outname, "deshunted.%s", inname);
	save_blob(outname, fdt3);

	PASS();
}
