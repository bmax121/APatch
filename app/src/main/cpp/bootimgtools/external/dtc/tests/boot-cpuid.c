// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * Copyright (C) 2008 David Gibson, IBM Corporation.
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
	uint32_t cpuid;

	test_init(argc, argv);

	if (argc != 3)
		CONFIG("Usage: %s <dtb file> <cpuid>", argv[0]);

	fdt = load_blob(argv[1]);
	cpuid = strtoul(argv[2], NULL, 0);

	if (fdt_boot_cpuid_phys(fdt) != cpuid)
		FAIL("Incorrect boot_cpuid_phys (0x%x instead of 0x%x)",
		     fdt_boot_cpuid_phys(fdt), cpuid);

	PASS();
}
