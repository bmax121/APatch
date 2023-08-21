// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_get_alias()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_alias(void *fdt, const char *path, const char *alias)
{
	const char *aliaspath;

	aliaspath = fdt_get_alias(fdt, alias);

	if (path && !aliaspath)
		FAIL("fdt_get_alias(%s) failed\n", alias);

	if (strcmp(aliaspath, path) != 0)
		FAIL("fdt_get_alias(%s) returned %s instead of %s\n",
		     alias, aliaspath, path);
}

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_alias(fdt, "/subnode@1", "s1");
	check_alias(fdt, "/subnode@1/subsubnode", "ss1");
	check_alias(fdt, "/subnode@1/subsubnode/subsubsubnode", "sss1");

	PASS();
}
