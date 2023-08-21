// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_check_header
 * Copyright (C) 2018 David Gibson
 */

#include <stdio.h>

#include <libfdt.h>

#include "tests.h"

static void *dtdup(void *dt)
{
	size_t bufsize = fdt_totalsize(dt);
	void *buf = xmalloc(bufsize);
	fdt_move(dt, buf, bufsize);
	return buf;
}

#define CHECK_MANGLE(exerr, code)					\
	do {								\
		void *fdt = dtdup(template);				\
		{ code }						\
		err = fdt_check_header(fdt);				\
		verbose_printf("\"%s\" => %s\n", #code, fdt_strerror(err)); \
		if (err != (exerr))					\
			FAIL("fdt_check_header() didn't catch mangle %s", \
			     #code);					\
		free(fdt);						\
	} while (0)

int main(int argc, char *argv[])
{
	void *template;
	int err;

	test_init(argc, argv);
	template = load_blob(argv[1]);

	/* Check that the base dt is valid before mangling it */
	err = fdt_check_header(template);
	if (err != 0)
		FAIL("Base tree fails: %s", fdt_strerror(err));

	/* Check a no-op mangle doesn't break things */
	CHECK_MANGLE(0, ; );

	/* Mess up the magic number */
	CHECK_MANGLE(-FDT_ERR_BADMAGIC,
		fdt_set_magic(fdt, fdt_magic(fdt) ^ 0x1);
	);
	CHECK_MANGLE(-FDT_ERR_BADMAGIC,
		fdt_set_magic(fdt, fdt_magic(fdt) ^ 0x80000000);
	);

	/* Mess up the version */
	CHECK_MANGLE(-FDT_ERR_BADVERSION,
		fdt_set_version(fdt, FDT_FIRST_SUPPORTED_VERSION - 1);
		fdt_set_last_comp_version(fdt, FDT_FIRST_SUPPORTED_VERSION - 1);
	);
	CHECK_MANGLE(-FDT_ERR_BADVERSION,
		fdt_set_version(fdt, FDT_LAST_SUPPORTED_VERSION + 1);
		fdt_set_last_comp_version(fdt, FDT_LAST_SUPPORTED_VERSION + 1);
	);
	CHECK_MANGLE(-FDT_ERR_BADVERSION,
		fdt_set_version(fdt, FDT_FIRST_SUPPORTED_VERSION);
		fdt_set_last_comp_version(fdt, FDT_LAST_SUPPORTED_VERSION);
	);

	/* Out of bounds sizes */
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, FDT_V1_SIZE - 1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		     fdt_set_totalsize(fdt, (uint32_t)INT_MAX + 1);
	);

	/* Truncate within various blocks */
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, fdt_off_dt_struct(fdt) - 1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, fdt_off_dt_strings(fdt) - 1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, fdt_off_mem_rsvmap(fdt) - 1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, fdt_off_dt_struct(fdt) + 1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, fdt_off_dt_strings(fdt) + 1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_totalsize(fdt, fdt_off_mem_rsvmap(fdt) + 1);
	);

	/* Negative block sizes */
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_size_dt_struct(fdt, (uint32_t)-1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_size_dt_strings(fdt, (uint32_t)-1);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		     fdt_set_size_dt_struct(fdt, (uint32_t)INT_MIN);
	);
	CHECK_MANGLE(-FDT_ERR_TRUNCATED,
		fdt_set_size_dt_strings(fdt, (uint32_t)INT_MIN);
	);

	PASS();
}
