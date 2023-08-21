// SPDX-License-Identifier: LGPL-2.1-or-later
/*
 * libfdt - Flat Device Tree manipulation
 *	Testcase for fdt_nop_node()
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

#define SPACE	65536

static enum {
	FIXED = 0,
	RESIZE,
	REALLOC,
	NEWALLOC,
} alloc_mode;

static void realloc_fdt(void **fdt, size_t *size, bool created)
{
	int err;

	switch (alloc_mode) {
	case FIXED:
		if (!(*fdt))
			*fdt = xmalloc(*size);
		else
			FAIL("Ran out of space");
		return;

	case RESIZE:
		if (!(*fdt)) {
			*fdt = xmalloc(SPACE);
		} else if (*size < SPACE) {
			*size += 1;
			err = fdt_resize(*fdt, *fdt, *size);
			if (err < 0)
				FAIL("fdt_resize() failed: %s",
				     fdt_strerror(err));
		} else {
			FAIL("Ran out of space");
		}		
		return;

	case REALLOC:
		*size += 1;
		*fdt = xrealloc(*fdt, *size);
		if (created) {
			err = fdt_resize(*fdt, *fdt, *size);
			if (err < 0)
				FAIL("fdt_resize() failed: %s",
				     fdt_strerror(err));
		}
		return;

	case NEWALLOC: {
		void *buf;

		*size += 1;
		buf = xmalloc(*size);
		if (created) {
			err = fdt_resize(*fdt, buf, *size);
			if (err < 0)
				FAIL("fdt_resize() failed: %s",
				     fdt_strerror(err));
		}
		free(*fdt);
		*fdt = buf;
		return;
	}

	default:
		CONFIG("Bad allocation mode");
	}
}

#define CHECK(code) \
	do {			      \
		err = (code);			     \
		if (err == -FDT_ERR_NOSPACE)			\
			realloc_fdt(&fdt, &size, created);		\
		else if (err)						\
			FAIL(#code ": %s", fdt_strerror(err));		\
	} while (err != 0)

int main(int argc, char *argv[])
{
	void *fdt = NULL;
	size_t size;
	int err;
	bool created = false;
	void *place;
	const char place_str[] = "this is a placeholder string\0string2";
	int place_len = sizeof(place_str);
	int create_flags;

	test_init(argc, argv);

	alloc_mode = FIXED;
	size = SPACE;
	create_flags = 0;

	if (argc == 2 || argc == 3) {
		if (streq(argv[1], "fixed")) {
			alloc_mode = FIXED;
			size = SPACE;
		} else if (streq(argv[1], "resize")) {
			alloc_mode = REALLOC;
			size = 0;
		} else if (streq(argv[1], "realloc")) {
			alloc_mode = REALLOC;
			size = 0;
		} else if (streq(argv[1], "newalloc")) {
			alloc_mode = NEWALLOC;
			size = 0;
		} else {
			char *endp;

			size = strtoul(argv[1], &endp, 0);
			if (*endp == '\0')
				alloc_mode = FIXED;
			else 
				CONFIG("Bad allocation mode \"%s\" specified",
				       argv[1]);
		}
	}
	if (argc == 3) {
		char *str = argv[2], *saveptr, *tok;
		bool default_flag = false;

		while ((tok = strtok_r(str, ",", &saveptr)) != NULL) {
			str = NULL;
			if (streq(tok, "default")) {
				default_flag = true;
			} else if (streq(tok, "no_name_dedup")) {
				create_flags |= FDT_CREATE_FLAG_NO_NAME_DEDUP;
			} else if (streq(tok, "bad")) {
				create_flags |= 0xffffffff;
			} else {
				CONFIG("Bad creation flags \"%s\" specified",
				       argv[2]);
			}
		}

		if (default_flag && create_flags != 0)
			CONFIG("Bad creation flags \"%s\" specified",
			       argv[2]);
	}

	if (argc > 3) {
		CONFIG("sw_tree1 [<allocation mode>] [<create flags>]");
	}

	fdt = xmalloc(size);
	CHECK(fdt_create_with_flags(fdt, size, create_flags));

	created = true;

	CHECK(fdt_add_reservemap_entry(fdt, TEST_ADDR_1, TEST_SIZE_1));

	CHECK(fdt_add_reservemap_entry(fdt, TEST_ADDR_2, TEST_SIZE_2));
	CHECK(fdt_finish_reservemap(fdt));

	CHECK(fdt_begin_node(fdt, ""));
	CHECK(fdt_property_string(fdt, "compatible", "test_tree1"));
	CHECK(fdt_property_u32(fdt, "prop-int", TEST_VALUE_1));
	CHECK(fdt_property_u64(fdt, "prop-int64", TEST_VALUE64_1));
	CHECK(fdt_property_string(fdt, "prop-str", TEST_STRING_1));
	CHECK(fdt_property_u32(fdt, "#address-cells", 1));
	CHECK(fdt_property_u32(fdt, "#size-cells", 0));

	CHECK(fdt_begin_node(fdt, "subnode@1"));
	CHECK(fdt_property_string(fdt, "compatible", "subnode1"));
	CHECK(fdt_property_u32(fdt, "reg", 1));
	CHECK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_1));
	CHECK(fdt_begin_node(fdt, "subsubnode"));
	CHECK(fdt_property(fdt, "compatible", "subsubnode1\0subsubnode",
			   23));
	CHECK(fdt_property_placeholder(fdt, "placeholder", place_len, &place));
	memcpy(place, place_str, place_len);
	CHECK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_1));
	CHECK(fdt_end_node(fdt));
	CHECK(fdt_begin_node(fdt, "ss1"));
	CHECK(fdt_end_node(fdt));
	CHECK(fdt_end_node(fdt));

	CHECK(fdt_begin_node(fdt, "subnode@2"));
	CHECK(fdt_property_u32(fdt, "reg", 2));
	CHECK(fdt_property_cell(fdt, "linux,phandle", PHANDLE_1));
	CHECK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_2));
	CHECK(fdt_property_u32(fdt, "#address-cells", 1));
	CHECK(fdt_property_u32(fdt, "#size-cells", 0));
	CHECK(fdt_begin_node(fdt, "subsubnode@0"));
	CHECK(fdt_property_u32(fdt, "reg", 0));
	CHECK(fdt_property_cell(fdt, "phandle", PHANDLE_2));
	CHECK(fdt_property(fdt, "compatible", "subsubnode2\0subsubnode",
			   23));
	CHECK(fdt_property_cell(fdt, "prop-int", TEST_VALUE_2));
	CHECK(fdt_end_node(fdt));
	CHECK(fdt_begin_node(fdt, "ss2"));
	CHECK(fdt_end_node(fdt));

	CHECK(fdt_end_node(fdt));

	CHECK(fdt_end_node(fdt));

	save_blob("unfinished_tree1.test.dtb", fdt);

	CHECK(fdt_finish(fdt));

	verbose_printf("Completed tree, totalsize = %d\n",
		       fdt_totalsize(fdt));

	save_blob("sw_tree1.test.dtb", fdt);

	PASS();
}
