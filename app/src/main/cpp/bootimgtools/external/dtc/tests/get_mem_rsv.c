// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_get_mem_rsv() and fdt_num_mem_rsv()
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
	int rc;

	test_init(argc, argv);
	fdt = load_blob_arg(argc, argv);

	rc = fdt_num_mem_rsv(fdt);
	if (rc < 0)
		FAIL("fdt_num_mem_rsv(): %s", fdt_strerror(rc));
	if (rc != 2)
		FAIL("fdt_num_mem_rsv() returned %d instead of 2", rc);

	check_mem_rsv(fdt, 0, TEST_ADDR_1, TEST_SIZE_1);
	check_mem_rsv(fdt, 1, TEST_ADDR_2, TEST_SIZE_2);
	PASS();
}
