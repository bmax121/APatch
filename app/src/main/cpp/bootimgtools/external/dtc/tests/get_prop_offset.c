// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_getprop_by_offset()
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
	bool found_prop_int = false;
	bool found_prop_str = false;
	int poffset;
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	fdt_for_each_property_offset(poffset, fdt, 0) {
		if (check_get_prop_offset_cell(fdt, poffset, "prop-int",
					       TEST_VALUE_1))
			found_prop_int = true;
		if (check_get_prop_offset(fdt, poffset, "prop-str",
					  strlen(TEST_STRING_1) + 1,
					  TEST_STRING_1))
			found_prop_str = true;
	}
	if (!found_prop_int)
		FAIL("Property 'prop-int' not found");
	if (!found_prop_str)
		FAIL("Property 'prop-str' not found");

	PASS();
}
