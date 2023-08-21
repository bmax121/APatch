// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for misbehaviour on a truncated string
 * Copyright (C) 2018 David Gibson, IBM Corporation.
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
	void *fdt = &truncated_string;
	const struct fdt_property *good, *bad;
	int off, len;
	const char *name;

	test_init(argc, argv);

	vg_prepare_blob(fdt, fdt_totalsize(fdt));

	off = fdt_first_property_offset(fdt, 0);
	good = fdt_get_property_by_offset(fdt, off, NULL);

	off = fdt_next_property_offset(fdt, off);
	bad = fdt_get_property_by_offset(fdt, off, NULL);

	if (fdt32_to_cpu(good->len) != 0)
		FAIL("Unexpected length for good property");
	name = fdt_get_string(fdt, fdt32_to_cpu(good->nameoff), &len);
	if (!name)
		FAIL("fdt_get_string() failed on good property: %s",
		     fdt_strerror(len));
	if (len != 4)
		FAIL("fdt_get_string() returned length %d (not 4) on good property",
		     len);
	if (!streq(name, "good"))
		FAIL("fdt_get_string() returned \"%s\" (not \"good\") on good property",
		     name);

	if (fdt32_to_cpu(bad->len) != 0)
		FAIL("Unexpected length for bad property\n");
	name = fdt_get_string(fdt, fdt32_to_cpu(bad->nameoff), &len);
	if (name)
		FAIL("fdt_get_string() succeeded incorrectly on bad property");
	else if (len != -FDT_ERR_TRUNCATED)
		FAIL("fdt_get_string() gave unexpected error on bad property: %s",
		     fdt_strerror(len));

	/* Make sure the 'good' property breaks correctly if we
	 * truncate the strings section */
	fdt_set_size_dt_strings(fdt, fdt32_to_cpu(good->nameoff) + 4);
	name = fdt_get_string(fdt, fdt32_to_cpu(good->nameoff), &len);
	if (name)
		FAIL("fdt_get_string() succeeded incorrectly on mangled property");
	else if (len != -FDT_ERR_TRUNCATED)
		FAIL("fdt_get_string() gave unexpected error on mangled property: %s",
		     fdt_strerror(len));

	PASS();
}
