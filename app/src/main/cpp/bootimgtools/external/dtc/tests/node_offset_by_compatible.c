// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_node_offset_by_compatible()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdarg.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_search(void *fdt, const char *compat, ...)
{
	va_list ap;
	int offset = -1, target;

	va_start(ap, compat);
	do {
		target = va_arg(ap, int);
		verbose_printf("Searching (target = %d): %d ->",
			       target, offset);
		offset = fdt_node_offset_by_compatible(fdt, offset, compat);
		verbose_printf("%d\n", offset);

		if (offset != target)
			FAIL("fdt_node_offset_by_compatible(%s) returns %d "
			     "instead of %d", compat, offset, target);
	} while (target >= 0);

	va_end(ap);
}

int main(int argc, char *argv[])
{
	void *fdt;
	int subnode1_offset, subnode2_offset;
	int subsubnode1_offset, subsubnode2_offset;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	subnode1_offset = fdt_path_offset(fdt, "/subnode@1");
	subnode2_offset = fdt_path_offset(fdt, "/subnode@2");
	subsubnode1_offset = fdt_path_offset(fdt, "/subnode@1/subsubnode");
	subsubnode2_offset = fdt_path_offset(fdt, "/subnode@2/subsubnode@0");

	if ((subnode1_offset < 0) || (subnode2_offset < 0)
	    || (subsubnode1_offset < 0) || (subsubnode2_offset < 0))
		FAIL("Can't find required nodes");

	check_search(fdt, "test_tree1", 0, -FDT_ERR_NOTFOUND);
	check_search(fdt, "subnode1", subnode1_offset, -FDT_ERR_NOTFOUND);
	check_search(fdt, "subsubnode1", subsubnode1_offset, -FDT_ERR_NOTFOUND);
	check_search(fdt, "subsubnode2", subsubnode2_offset, -FDT_ERR_NOTFOUND);
	/* Eek.. HACK to make this work whatever the order in the
	 * example tree */
	if (subsubnode1_offset < subsubnode2_offset)
		check_search(fdt, "subsubnode", subsubnode1_offset,
			     subsubnode2_offset, -FDT_ERR_NOTFOUND);
	else
		check_search(fdt, "subsubnode", subsubnode2_offset,
			     subsubnode1_offset, -FDT_ERR_NOTFOUND);
	check_search(fdt, "nothing-like-this", -FDT_ERR_NOTFOUND);

	PASS();
}
