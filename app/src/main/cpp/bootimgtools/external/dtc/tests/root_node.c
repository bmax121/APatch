// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Basic testcase for read-only access
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
	const struct fdt_node_header *nh;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	nh = fdt_offset_ptr(fdt, 0, sizeof(*nh));

	if (! nh)
		FAIL("NULL retrieving root node");

	if (fdt32_to_cpu(nh->tag) != FDT_BEGIN_NODE)
		FAIL("Wrong tag on root node");

	if (strlen(nh->name) != 0)
		FAIL("Wrong name for root node, \"%s\" instead of empty",
		     nh->name);

	PASS();
}
