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

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_getprop(fdt, 0, "escape-str",
		      strlen(TEST_STRING_2)+1, TEST_STRING_2);
	check_getprop(fdt, 0, "escape-str-2",
		      strlen(TEST_STRING_3)+1, TEST_STRING_3);

	PASS();
}
