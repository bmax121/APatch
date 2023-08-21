// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for phandle format options
 * Copyright (C) 2009 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

#define PHANDLE_LEGACY	0x1
#define PHANDLE_EPAPR	0x2
#define PHANDLE_BOTH	0x3

int main(int argc, char *argv[])
{
	void *fdt;
	int phandle_format;
	int n4;
	uint32_t h4;

	if (argc != 3)
		CONFIG("Usage: %s <dtb file> <legacy|epapr|both>\n", argv[0]);

	test_init(argc, argv);
	fdt = load_blob(argv[1]);

	if (streq(argv[2], "legacy"))
		phandle_format = PHANDLE_LEGACY;
	else if (streq(argv[2], "epapr"))
		phandle_format = PHANDLE_EPAPR;
	else if (streq(argv[2], "both"))
		phandle_format = PHANDLE_BOTH;
	else
		CONFIG("Usage: %s <dtb file> <legacy|epapr|both>\n", argv[0]);

	n4 = fdt_path_offset(fdt, "/node4");
	if (n4 < 0)
		FAIL("fdt_path_offset(/node4): %s", fdt_strerror(n4));

	h4 = fdt_get_phandle(fdt, n4);
	if ((h4 == 0) || (h4 == ~0U))
		FAIL("/node4 has bad phandle 0x%x\n", h4);

	if (phandle_format & PHANDLE_LEGACY)
		check_getprop_cell(fdt, n4, "linux,phandle", h4);
	else
		if (fdt_getprop(fdt, n4, "linux,phandle", NULL))
			FAIL("linux,phandle property present in non-legacy mode");

	if (phandle_format & PHANDLE_EPAPR)
		check_getprop_cell(fdt, n4, "phandle", h4);
	else
		if (fdt_getprop(fdt, n4, "phandle", NULL))
			FAIL("phandle property present in legacy-only mode");

	PASS();
}
