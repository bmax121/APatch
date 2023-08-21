// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_getprop()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 * Copyright (C) 2012 NVIDIA CORPORATION. All rights reserved.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_getprop_cell(fdt, 0, "#address-cells", 1);
	check_getprop_cell(fdt, 0, "#gpio-cells", 2);

	PASS();
}
