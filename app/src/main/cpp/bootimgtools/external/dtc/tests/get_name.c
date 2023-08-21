// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_get_name()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_name(void *fdt, const char *path)
{
	int offset;
	const char *getname, *getname2, *checkname;
	int len;

	checkname = strrchr(path, '/');
	if (!checkname)
		TEST_BUG();
	checkname += 1;

	offset = fdt_path_offset(fdt, path);
	if (offset < 0)
		FAIL("Couldn't find %s", path);

	getname = fdt_get_name(fdt, offset, &len);
	verbose_printf("fdt_get_name(%d) returns \"%s\" (len=%d)\n",
		       offset, getname, len);
	if (!getname)
		FAIL("fdt_get_name(%d): %s", offset, fdt_strerror(len));
	if (len < 0)
		FAIL("negative name length (%d) for returned node name\n", len);

	if (strcmp(getname, checkname) != 0)
		FAIL("fdt_get_name(%s) returned \"%s\" instead of \"%s\"",
		     path, getname, checkname);

	if ((unsigned)len != strlen(getname))
		FAIL("fdt_get_name(%s) returned length %d instead of %zd",
		     path, len, strlen(getname));

	/* Now check that it doesn't break if we omit len */
	getname2 = fdt_get_name(fdt, offset, NULL);
	if (!getname2)
		FAIL("fdt_get_name(%d, NULL) failed", offset);
	if (strcmp(getname2, getname) != 0)
		FAIL("fdt_get_name(%d, NULL) returned \"%s\" instead of \"%s\"",
		     offset, getname2, getname);
}

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	check_name(fdt, "/");
	check_name(fdt, "/subnode@1");
	check_name(fdt, "/subnode@2");
	check_name(fdt, "/subnode@1/subsubnode");
	check_name(fdt, "/subnode@2/subsubnode@0");

	PASS();
}
