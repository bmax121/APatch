#include <fdt.h>
#include "testdata.h"

	.macro	fdtlong	val
	.byte	((\val) >> 24) & 0xff
	.byte	((\val) >> 16) & 0xff
	.byte	((\val) >> 8) & 0xff
	.byte	(\val) & 0xff
	.endm

	.macro	treehdr	tree
	.balign	8
	.globl	\tree
\tree :
	fdtlong	FDT_MAGIC
	fdtlong	(\tree\()_end - \tree)
	fdtlong	(\tree\()_struct - \tree)
	fdtlong	(\tree\()_strings - \tree)
	fdtlong	(\tree\()_rsvmap - \tree)
	fdtlong	0x11
	fdtlong	0x10
	fdtlong	0
	fdtlong	(\tree\()_strings_end - \tree\()_strings)
	fdtlong	(\tree\()_struct_end - \tree\()_struct)
	.endm

	.macro	rsvmape	addrh, addrl, lenh, lenl
	fdtlong	\addrh
	fdtlong	\addrl
	fdtlong	\lenh
	fdtlong	\lenl
	.endm

	.macro	empty_rsvmap	tree
	.balign	8
\tree\()_rsvmap:
	rsvmape	0, 0, 0, 0
\tree\()_rsvmap_end:
	.endm

	.macro prophdr	tree, name, len
	fdtlong	FDT_PROP
	fdtlong	\len
	fdtlong	(\tree\()_\name - \tree\()_strings)
	.endm

	.macro propnil	tree, name
	prophdr	\tree, \name, 0
	.endm

	.macro propu32	tree, name, val
	prophdr	\tree, \name, 4
	fdtlong	\val
	.endm

	.macro propu64	tree, name, valh, vall
	prophdr	\tree, \name, 8
	fdtlong	\valh
	fdtlong	\vall
	.endm

	.macro propstr	tree, name, str:vararg
	prophdr	\tree, \name, (55f - 54f)
54:
	.asciz	\str
55:
	.balign	4
	.endm

	.macro	beginn	name:vararg
	fdtlong	FDT_BEGIN_NODE
	.asciz	\name
	.balign 4
	.endm

	.macro	endn
	fdtlong	FDT_END_NODE
	.endm

	.macro	string	tree, name, str:vararg
\tree\()_\name :
	.asciz	\str
	.endm


	.data

	treehdr	test_tree1

	.balign	8
test_tree1_rsvmap:
	rsvmape	TEST_ADDR_1H, TEST_ADDR_1L, TEST_SIZE_1H, TEST_SIZE_1L
	rsvmape	TEST_ADDR_2H, TEST_ADDR_2L, TEST_SIZE_2H, TEST_SIZE_2L
	rsvmape	0, 0, 0, 0
test_tree1_rsvmap_end:

test_tree1_struct:
	beginn	""
	propstr	test_tree1, compatible, "test_tree1"
	propu32	test_tree1, prop_int, TEST_VALUE_1
	propu64	test_tree1, prop_int64, TEST_VALUE64_1H, TEST_VALUE64_1L
	propstr	test_tree1, prop_str, TEST_STRING_1
	propu32 test_tree1, address_cells, 1
	propu32	test_tree1, size_cells, 0

	beginn	"subnode@1"
	propstr	test_tree1, compatible, "subnode1"
	propu32	test_tree1, reg, 1
	propu32	test_tree1, prop_int, TEST_VALUE_1

	beginn	"subsubnode"
	propstr	test_tree1, compatible, "subsubnode1\0subsubnode"
	propstr	test_tree1, placeholder, "this is a placeholder string\0string2"
	propu32	test_tree1, prop_int, TEST_VALUE_1
	endn

	beginn	"ss1"
	endn

	endn

	beginn	"subnode@2"
	propu32	test_tree1, reg, 2
	propu32	test_tree1, linux_phandle, PHANDLE_1
	propu32	test_tree1, prop_int, TEST_VALUE_2
	propu32	test_tree1, address_cells, 1
	propu32	test_tree1, size_cells, 0

	beginn	"subsubnode@0"
	propu32	test_tree1, reg, 0
	propu32	test_tree1, phandle, PHANDLE_2
	propstr	test_tree1, compatible, "subsubnode2\0subsubnode"
	propu32	test_tree1, prop_int, TEST_VALUE_2
	endn

	beginn	"ss2"
	endn

	endn

	endn
	fdtlong	FDT_END
test_tree1_struct_end:

test_tree1_strings:
	string	test_tree1, compatible, "compatible"
	string	test_tree1, prop_int, "prop-int"
	string	test_tree1, prop_int64, "prop-int64"
	string	test_tree1, prop_str, "prop-str"
	string	test_tree1, linux_phandle, "linux,phandle"
	string	test_tree1, phandle, "phandle"
	string	test_tree1, reg, "reg"
	string	test_tree1, placeholder, "placeholder"
	string	test_tree1, address_cells, "#address-cells"
	string	test_tree1, size_cells, "#size-cells"
test_tree1_strings_end:
test_tree1_end:


	treehdr	truncated_property
	empty_rsvmap	truncated_property

truncated_property_struct:
	beginn	""
	prophdr	truncated_property, prop_truncated, 4
	/* Oops, no actual property data here */
truncated_property_struct_end:

truncated_property_strings:
	string	truncated_property, prop_truncated, "truncated"
truncated_property_strings_end:

truncated_property_end:


	treehdr	bad_node_char
	empty_rsvmap	bad_node_char

bad_node_char_struct:
	beginn	""
	beginn	"sub$node"
	endn
	endn
	fdtlong	FDT_END
bad_node_char_struct_end:

bad_node_char_strings:
bad_node_char_strings_end:
bad_node_char_end:


	treehdr	bad_node_format
	empty_rsvmap	bad_node_format

bad_node_format_struct:
	beginn	""
	beginn	"subnode@1@2"
	endn
	endn
	fdtlong	FDT_END
bad_node_format_struct_end:

bad_node_format_strings:
bad_node_format_strings_end:
bad_node_format_end:


	treehdr	bad_prop_char
	empty_rsvmap	bad_prop_char

bad_prop_char_struct:
	beginn	""
	propu32	bad_prop_char, prop, TEST_VALUE_1
	endn
	fdtlong	FDT_END
bad_prop_char_struct_end:

bad_prop_char_strings:
	string	bad_prop_char, prop, "prop$erty"
bad_prop_char_strings_end:
bad_prop_char_end:


	/* overflow_size_strings */
	.balign	8
	.globl	ovf_size_strings
ovf_size_strings:
	fdtlong	FDT_MAGIC
	fdtlong	(ovf_size_strings_end - ovf_size_strings)
	fdtlong	(ovf_size_strings_struct - ovf_size_strings)
	fdtlong	(ovf_size_strings_strings - ovf_size_strings)
	fdtlong	(ovf_size_strings_rsvmap - ovf_size_strings)
	fdtlong	0x11
	fdtlong	0x10
	fdtlong	0
	fdtlong	0xffffffff
	fdtlong	(ovf_size_strings_struct_end - ovf_size_strings_struct)
	empty_rsvmap	ovf_size_strings

ovf_size_strings_struct:
	beginn	""
	propu32	ovf_size_strings, bad_string, 0
	endn
	fdtlong	FDT_END
ovf_size_strings_struct_end:

ovf_size_strings_strings:
	string	ovf_size_strings, x, "x"
	ovf_size_strings_bad_string = ovf_size_strings_strings + 0x10000000
ovf_size_strings_strings_end:
ovf_size_strings_end:


	/* truncated_string */
	treehdr	truncated_string
	empty_rsvmap	truncated_string

truncated_string_struct:
	beginn	""
	propnil	truncated_string, good_string
	propnil	truncated_string, bad_string
	endn
	fdtlong	FDT_END
truncated_string_struct_end:

truncated_string_strings:
	string	truncated_string, good_string, "good"
truncated_string_bad_string:
	.ascii	"bad"
	/* NOTE: terminating \0 deliberately missing */
truncated_string_strings_end:
truncated_string_end:


	/* truncated_memrsv */
	treehdr	truncated_memrsv

truncated_memrsv_struct:
	beginn	""
	endn
	fdtlong	FDT_END
truncated_memrsv_struct_end:

truncated_memrsv_strings:
truncated_memrsv_strings_end:

	.balign	8
truncated_memrsv_rsvmap:
	rsvmape	TEST_ADDR_1H, TEST_ADDR_1L, TEST_SIZE_1H, TEST_SIZE_1L
truncated_memrsv_rsvmap_end:

truncated_memrsv_end:


        /* two root nodes */
	treehdr	two_roots
	empty_rsvmap	two_roots

two_roots_struct:
	beginn	""
	endn
	beginn	""
	endn
	fdtlong	FDT_END
two_roots_struct_end:

two_roots_strings:
two_roots_strings_end:

two_roots_end:


        /* root node with a non-empty name */
	treehdr	named_root
	empty_rsvmap	named_root

named_root_struct:
	beginn	"fake"
	endn
	fdtlong	FDT_END
named_root_struct_end:

named_root_strings:
named_root_strings_end:

named_root_end:
