// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_nop_property()
 * Copyright (C) 2006 David Gibson, IBM Corporation.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

int main(int argc, char *argv[])
{
	void *fdt;
	const uint32_t *intp;
	const char *strp;
	int err;
	int lenerr;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	intp = check_getprop_cell(fdt, 0, "prop-int", TEST_VALUE_1);
	verbose_printf("int value was 0x%08x\n", *intp);

	err = fdt_nop_property(fdt, 0, "prop-int");
	if (err)
		FAIL("Failed to nop \"prop-int\": %s", fdt_strerror(err));

	intp = fdt_getprop(fdt, 0, "prop-int", &lenerr);
	if (intp)
		FAIL("prop-int still present after nopping");
	if (lenerr != -FDT_ERR_NOTFOUND)
		FAIL("Unexpected error on second getprop: %s", fdt_strerror(err));

	strp = check_getprop(fdt, 0, "prop-str", strlen(TEST_STRING_1)+1,
			     TEST_STRING_1);
	verbose_printf("string value was \"%s\"\n", strp);
	err = fdt_nop_property(fdt, 0, "prop-str");
	if (err)
		FAIL("Failed to nop \"prop-str\": %s", fdt_strerror(err));

	strp = fdt_getprop(fdt, 0, "prop-str", &lenerr);
	if (strp)
		FAIL("prop-str still present after nopping");
	if (lenerr != -FDT_ERR_NOTFOUND)
		FAIL("Unexpected error on second getprop: %s", fdt_strerror(err));

	PASS();
}
