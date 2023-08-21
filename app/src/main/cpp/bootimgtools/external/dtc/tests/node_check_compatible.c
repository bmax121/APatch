// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_node_check_compatible()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_compatible(const void *fdt, const char *path,
			     const char *compat)
{
	int offset, err;

	offset = fdt_path_offset(fdt, path);
	if (offset < 0)
		FAIL("fdt_path_offset(%s): %s", path, fdt_strerror(offset));

	err = fdt_node_check_compatible(fdt, offset, compat);
	if (err < 0)
		FAIL("fdt_node_check_compatible(%s): %s", path,
		     fdt_strerror(err));
	if (err != 0)
		FAIL("%s is not compatible with \"%s\"", path, compat);
}

static void check_not_compatible(const void *fdt, const char *path,
				 const char *compat)
{
	int offset, err;

	offset = fdt_path_offset(fdt, path);
	if (offset < 0)
		FAIL("fdt_path_offset(%s): %s", path, fdt_strerror(offset));

	err = fdt_node_check_compatible(fdt, offset, compat);
	if (err < 0)
		FAIL("fdt_node_check_compatible(%s): %s", path,
		     fdt_strerror(err));
	if (err == 0)
		FAIL("%s is incorrectly compatible with \"%s\"", path, compat);
}

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_compatible(fdt, "/", "test_tree1");
	check_compatible(fdt, "/subnode@1/subsubnode", "subsubnode1");
	check_compatible(fdt, "/subnode@1/subsubnode", "subsubnode");
	check_not_compatible(fdt, "/subnode@1/subsubnode", "subsubnode2");
	check_compatible(fdt, "/subnode@2/subsubnode", "subsubnode2");
	check_compatible(fdt, "/subnode@2/subsubnode", "subsubnode");
	check_not_compatible(fdt, "/subnode@2/subsubnode", "subsubnode1");

	PASS();
}
