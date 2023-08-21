// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for behaviour on searching for a non-existent node
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_error(const char *s, int err)
{
	if (err != -FDT_ERR_NOTFOUND)
		FAIL("%s return error %s instead of -FDT_ERR_NOTFOUND", s,
		     fdt_strerror(err));
}

int main(int argc, char *argv[])
{
	void *fdt;
	int offset;
	int subnode1_offset;
	int lenerr;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	fdt_get_property(fdt, 0, "nonexistant-property", &lenerr);
	check_error("fdt_get_property(\"nonexistant-property\")", lenerr);

	fdt_getprop(fdt, 0, "nonexistant-property", &lenerr);
	check_error("fdt_getprop(\"nonexistant-property\"", lenerr);

	subnode1_offset = fdt_subnode_offset(fdt, 0, "subnode@1");
	if (subnode1_offset < 0)
		FAIL("Couldn't find subnode1: %s", fdt_strerror(subnode1_offset));

	fdt_getprop(fdt, subnode1_offset, "prop-str", &lenerr);
	check_error("fdt_getprop(\"prop-str\")", lenerr);

	offset = fdt_subnode_offset(fdt, 0, "nonexistant-subnode");
	check_error("fdt_subnode_offset(\"nonexistant-subnode\")", offset);

	offset = fdt_subnode_offset(fdt, 0, "subsubnode");
	check_error("fdt_subnode_offset(\"subsubnode\")", offset);

	offset = fdt_path_offset(fdt, "/nonexistant-subnode");
	check_error("fdt_path_offset(\"/nonexistant-subnode\")", offset);

	PASS();
}
