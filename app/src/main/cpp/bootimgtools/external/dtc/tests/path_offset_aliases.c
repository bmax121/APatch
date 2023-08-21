// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_path_offset()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 * Copyright 2008 Kumar Gala, Freescale Semiconductor, Inc.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_alias(void *fdt, const char *full_path, const char *alias_path)
{
	int offset, offset_a;

	offset = fdt_path_offset(fdt, full_path);
	offset_a = fdt_path_offset(fdt, alias_path);

	if (offset != offset_a)
		FAIL("Mismatch between %s path_offset (%d) and %s path_offset alias (%d)",
		     full_path, offset, alias_path, offset_a);
}

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_alias(fdt, "/subnode@1", "s1");
	check_alias(fdt, "/subnode@1/subsubnode", "ss1");
	check_alias(fdt, "/subnode@1/subsubnode", "s1/subsubnode");
	check_alias(fdt, "/subnode@1/subsubnode/subsubsubnode", "sss1");
	check_alias(fdt, "/subnode@1/subsubnode/subsubsubnode", "ss1/subsubsubnode");
	check_alias(fdt, "/subnode@1/subsubnode/subsubsubnode", "s1/subsubnode/subsubsubnode");

	PASS();
}
