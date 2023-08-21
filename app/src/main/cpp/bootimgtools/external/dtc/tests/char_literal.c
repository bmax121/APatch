// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for character literals in dtc
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 * Copyright (C) 2011 The Chromium Authors. All rights reserved.
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
	void *fdt;
	fdt32_t expected_cells[5];

	expected_cells[0] = cpu_to_fdt32((unsigned char)TEST_CHAR1);
	expected_cells[1] = cpu_to_fdt32((unsigned char)TEST_CHAR2);
	expected_cells[2] = cpu_to_fdt32((unsigned char)TEST_CHAR3);
	expected_cells[3] = cpu_to_fdt32((unsigned char)TEST_CHAR4);
	expected_cells[4] = cpu_to_fdt32((unsigned char)TEST_CHAR5);

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_getprop(fdt, 0, "char-literal-cells",
		      sizeof(expected_cells), expected_cells);

	PASS();
}
