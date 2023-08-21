// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * dumptrees - utility for libfdt testing
 *
 * (C) Copyright David Gibson <dwg@au1.ibm.com>, IBM Corporation.  2006.
 */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdint.h>

#include <libfdt.h>

#include "testdata.h"

static struct {
	void *blob;
	const char *filename;
} trees[] = {
#define TREE(name)	{ &name, #name ".dtb" }
	TREE(test_tree1),
	TREE(bad_node_char), TREE(bad_node_format), TREE(bad_prop_char),
	TREE(ovf_size_strings),
	TREE(truncated_property), TREE(truncated_string),
	TREE(truncated_memrsv),
	TREE(two_roots),
	TREE(named_root)
};

#define NUM_TREES	(sizeof(trees) / sizeof(trees[0]))

int main(int argc, char *argv[])
{
	unsigned int i;

	if (argc != 2) {
	    fprintf(stderr, "Missing output directory argument\n");
	    return 1;
	}

	if (chdir(argv[1]) != 0) {
	    perror("chdir()");
	    return 1;
	}

	for (i = 0; i < NUM_TREES; i++) {
		void *blob = trees[i].blob;
		const char *filename = trees[i].filename;
		int size;
		int fd;
		int ret;

		size = fdt_totalsize(blob);

		printf("Tree \"%s\", %d bytes\n", filename, size);

		fd = open(filename, O_WRONLY | O_CREAT | O_TRUNC, 0666);
		if (fd < 0)
			perror("open()");

		ret = write(fd, blob, size);
		if (ret != size)
			perror("write()");

		close(fd);
	}
	exit(0);
}
