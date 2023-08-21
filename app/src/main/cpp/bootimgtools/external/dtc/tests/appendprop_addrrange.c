// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_appendprop_addrrange()
 * Copyright (C) 2018 AKASHI Takahiro, Linaro Limited
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

int main(int argc, char *argv[])
{
	void *fdt, *buf;
	int offset, xac, xsc, num, i, err;
	uint64_t addr, size;

	if (argc != 5)
		CONFIG("Usage: %s <dtb file> <address-cells> <size-cells> <num>\n",
		       argv[0]);

	test_init(argc, argv);
	fdt = load_blob(argv[1]);
	xac = strtol(argv[2], NULL, 10);
	xsc = strtol(argv[3], NULL, 10);
	num = strtol(argv[4], NULL, 10);

	buf = xmalloc(0x1000);
	if (!buf)
		FAIL("Couldn't allocate temporary buffer");
	err = fdt_open_into(fdt, buf, 0x1000);
	if (err)
		FAIL("fdt_open_into(): %s", fdt_strerror(err));

	fdt = buf;

	/* Set up */
	err = fdt_setprop_cell(fdt, 0, "#address-cells", xac);
	if (err)
		FAIL("fdt_setprop_cell(\"#address-cells\"): %s",
		     fdt_strerror(err));
	err = fdt_setprop_cell(fdt, 0, "#size-cells", xsc);
	if (err)
		FAIL("fdt_setprop_cell(\"#size-cells\"): %s",
		     fdt_strerror(err));

	offset = fdt_path_offset(fdt, "/node@1");
	if (offset < 0)
		FAIL("Couldn't find path %s", "/node@1");

	addr = TEST_MEMREGION_ADDR;
	if (xac > 1)
		addr += TEST_MEMREGION_ADDR_HI;
	size = TEST_MEMREGION_SIZE;
	if (xsc > 1)
		size += TEST_MEMREGION_SIZE_HI;

	/*
	 * Do test
	 */
	/* 1. repeat append's */
	for (i = 0; i < num; i++) {
		err = fdt_appendprop_addrrange(fdt, 0, offset,
					       "prop-memregion", addr, size);
		if (err)
			FAIL("Failed to append[%d] \"prop-memregion\": %s",
			     i, fdt_strerror(err));

		check_getprop_addrrange(fdt, 0, offset, "prop-memregion",
					i + 1);

		addr += size;
		size += TEST_MEMREGION_SIZE_INC;
	}

	/* 2. default property name */
	addr = TEST_MEMREGION_ADDR;
	if (xac > 1)
		addr += TEST_MEMREGION_ADDR_HI;
	size = TEST_MEMREGION_SIZE;
	if (xsc > 1)
		size += TEST_MEMREGION_SIZE_HI;

	err = fdt_appendprop_addrrange(fdt, 0, offset, "reg", addr, size);
	if (err)
		FAIL("Failed to set \"reg\": %s", fdt_strerror(err));
	check_getprop_addrrange(fdt, 0, offset, "reg", 1);

	PASS();
}
