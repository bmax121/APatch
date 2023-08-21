// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for #address-cells and #size-cells handling
 * Copyright (C) 2014 David Gibson, <david@gibson.dropbear.id.au>
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_node(const void *fdt, const char *path, int ac, int sc)
{
	int offset;
	int xac, xsc;

	offset = fdt_path_offset(fdt, path);
	if (offset < 0)
		FAIL("Couldn't find path %s", path);

	xac = fdt_address_cells(fdt, offset);
	xsc = fdt_size_cells(fdt, offset);

	if (xac != ac)
		FAIL("Address cells for %s is %d instead of %d\n",
		     path, xac, ac);
	if (xsc != sc)
		FAIL("Size cells for %s is %d instead of %d\n",
		     path, xsc, sc);
}

int main(int argc, char *argv[])
{
	void *fdt;

	if (argc != 2)
		CONFIG("Usage: %s <dtb file>\n", argv[0]);

	test_init(argc, argv);
	fdt = load_blob(argv[1]);

	check_node(fdt, "/", 2, 1);
	PASS();
}
