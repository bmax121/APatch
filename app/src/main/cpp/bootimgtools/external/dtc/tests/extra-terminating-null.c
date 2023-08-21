// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for properties with more than one terminating null
 * Copyright (C) 2009 David Gibson, IBM Corporation.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#include <libfdt.h>

#include "tests.h"
#include "testdata.h"

static void check_extranull(void *fdt, const char *prop, const char *str, int numnulls)
{
	int len = strlen(str);
	char checkbuf[len+numnulls];

	memset(checkbuf, 0, sizeof(checkbuf));
	memcpy(checkbuf, TEST_STRING_1, len);

	check_getprop(fdt, 0, prop, len+numnulls, checkbuf);
}

int main(int argc, char *argv[])
{
	void *fdt;

	test_init(argc, argv);

	fdt = load_blob_arg(argc, argv);

	check_extranull(fdt, "extranull0", TEST_STRING_1, 1);
	check_extranull(fdt, "extranull1,1", TEST_STRING_1, 2);
	check_extranull(fdt, "extranull1,2", TEST_STRING_1, 2);
	check_extranull(fdt, "extranull2,1", TEST_STRING_1, 3);
	check_extranull(fdt, "extranull2,2", TEST_STRING_1, 3);
	check_extranull(fdt, "extranull2,3", TEST_STRING_1, 3);
	check_extranull(fdt, "extranull2,4", TEST_STRING_1, 3);

	PASS();
}
