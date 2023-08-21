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
	void *fdt, *fdt1;
	void *buf;
	int oldsize, bufsize, packsize;
	int err;
	const char *inname;
	char outname[PATH_MAX];

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);
	inname = argv[1];

	oldsize = fdt_totalsize(fdt);

	bufsize = oldsize * 2;

	buf = xmalloc(bufsize);
	/* don't leak uninitialized memory into our output */
	memset(buf, 0, bufsize);

	fdt1 = buf;
	err = fdt_open_into(fdt, fdt1, bufsize);
	if (err)
		FAIL("fdt_open_into(): %s", fdt_strerror(err));
	sprintf(outname, "opened.%s", inname);
	save_blob(outname, fdt1);

	err = fdt_pack(fdt1);
	if (err)
		FAIL("fdt_pack(): %s", fdt_strerror(err));
	sprintf(outname, "repacked.%s", inname);
	save_blob(outname, fdt1);

	packsize = fdt_totalsize(fdt1);

	verbose_printf("oldsize = %d, bufsize = %d, packsize = %d\n",
		       oldsize, bufsize, packsize);
	PASS();
}
