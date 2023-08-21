# SPDX-License-Identifier: (GPL-2.0-or-later OR BSD-2-Clause)
# pylibfdt - Tests for Flat Device Tree manipulation in Python
# Copyright (C) 2017 Google, Inc.
# Written by Simon Glass <sjg@chromium.org>
#

import struct
import sys
import types
import unittest

sys.path.insert(0, '../pylibfdt')
import libfdt
from libfdt import Fdt, FdtSw, FdtException, QUIET_NOTFOUND, QUIET_ALL

TEST_ADDR_1H = 0xdeadbeef
TEST_ADDR_1L = 0x00000000
TEST_ADDR_1 = (TEST_ADDR_1H << 32) | TEST_ADDR_1L
TEST_ADDR_1 = 0x8000000000000000
TEST_SIZE_1H = 0x00000000
TEST_SIZE_1L = 0x00100000
TEST_SIZE_1 = (TEST_SIZE_1H << 32) | TEST_SIZE_1L
TEST_ADDR_2H = 0
TEST_ADDR_2L = 123456789
TEST_ADDR_2 = (TEST_ADDR_2H << 32) | TEST_ADDR_2L
TEST_SIZE_2H = 0
TEST_SIZE_2L = 0o10000
TEST_SIZE_2 = (TEST_SIZE_2H << 32) | TEST_SIZE_2L

TEST_VALUE_1 = 0xdeadbeef
TEST_VALUE_2 = 123456789

TEST_VALUE64_1H = 0xdeadbeef
TEST_VALUE64_1L = 0x01abcdef
TEST_VALUE64_1 = (TEST_VALUE64_1H << 32) | TEST_VALUE64_1L

PHANDLE_1 = 0x2000
PHANDLE_2 = 0x2001

TEST_BYTES_1 = b'hello world'

TEST_STRING_1 = 'hello world'
TEST_STRING_2 = 'hi world'
TEST_STRING_3 = u'unicode \u01d3'


def get_err(err_code):
    """Convert an error code into an error message

    Args:
        err_code: Error code value (FDT_ERR_...)

    Returns:
        String error code
    """
    return 'pylibfdt error %d: %s' % (-err_code, libfdt.strerror(-err_code))

def _ReadFdt(fname):
    """Read a device tree file into an Fdt object, ready for use

    Args:
        fname: Filename to read from

    Returns:
        Fdt bytearray suitable for passing to libfdt functions
    """
    with open(fname, mode='rb') as f:
        return libfdt.Fdt(f.read())

class PyLibfdtBasicTests(unittest.TestCase):
    """Test class for basic pylibfdt access functions

    Properties:
        fdt: Device tree file used for testing
    """

    def setUp(self):
        """Read in the device tree we use for testing"""
        self.fdt = _ReadFdt('test_tree1.dtb')
        self.fdt2 = _ReadFdt('test_props.dtb')
        self.fdt3 = _ReadFdt('aliases.dtb')

    def GetPropList(self, node_path):
        """Read a list of properties from a node

        Args:
            node_path: Full path to node, e.g. '/subnode@1/subsubnode'

        Returns:
            List of property names for that node, e.g. ['compatible', 'reg']
        """
        prop_list = []
        node = self.fdt.path_offset(node_path)
        poffset = self.fdt.first_property_offset(node, QUIET_NOTFOUND)
        while poffset > 0:
            prop = self.fdt.get_property_by_offset(poffset)
            prop_list.append(prop.name)
            poffset = self.fdt.next_property_offset(poffset, QUIET_NOTFOUND)
        return prop_list

    def GetSubnodes(self, node_path):
        """Read a list of subnodes from a node

        Args:
            node_path: Full path to node, e.g. '/subnode@1/subsubnode'

        Returns:
            List of subnode names for that node, e.g. ['subsubnode', 'ss1']
        """
        subnode_list = []
        node = self.fdt.path_offset(node_path)
        offset = self.fdt.first_subnode(node, QUIET_NOTFOUND)
        while offset > 0:
            name = self.fdt.get_name(offset)
            subnode_list.append(name)
            offset = self.fdt.next_subnode(offset, QUIET_NOTFOUND)
        return subnode_list

    def testImport(self):
        """Check that we can import the library correctly"""
        self.assertEqual(type(libfdt), types.ModuleType)

    def testBadFdt(self):
        """Check that a filename provided accidentally is not accepted"""
        with self.assertRaises(FdtException) as e:
            fdt = libfdt.Fdt(b'a string')
        self.assertEqual(e.exception.err, -libfdt.BADMAGIC)

    def testSubnodeOffset(self):
        """check that we can locate a subnode by name"""
        node1 = self.fdt.path_offset('/subnode@1')
        self.assertEqual(self.fdt.subnode_offset(0, 'subnode@1'), node1)

        with self.assertRaises(FdtException) as e:
            self.fdt.subnode_offset(0, 'missing')
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)

        node2 = self.fdt.path_offset('/subnode@1/subsubnode')
        self.assertEqual(self.fdt.subnode_offset(node1, 'subsubnode'), node2)

    def testPathOffset(self):
        """Check that we can find the offset of a node"""
        self.assertEqual(self.fdt.path_offset('/'), 0)
        self.assertTrue(self.fdt.path_offset('/subnode@1') > 0)
        with self.assertRaises(FdtException) as e:
            self.fdt.path_offset('/wibble')
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)
        self.assertEqual(self.fdt.path_offset('/wibble', QUIET_NOTFOUND),
                          -libfdt.NOTFOUND)

    def testPropertyOffset(self):
        """Walk through all the properties in the root node"""
        offset = self.fdt.first_property_offset(0)
        self.assertTrue(offset > 0)
        for i in range(5):
            next_offset = self.fdt.next_property_offset(offset)
            self.assertTrue(next_offset > offset)
            offset = next_offset
        self.assertEqual(self.fdt.next_property_offset(offset, QUIET_NOTFOUND),
                          -libfdt.NOTFOUND)

    def testPropertyOffsetExceptions(self):
        """Check that exceptions are raised as expected"""
        with self.assertRaises(FdtException) as e:
            self.fdt.first_property_offset(107)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)

        # Quieten the NOTFOUND exception and check that a BADOFFSET
        # exception is still raised.
        with self.assertRaises(FdtException) as e:
            self.fdt.first_property_offset(107, QUIET_NOTFOUND)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)
        with self.assertRaises(FdtException) as e:
            self.fdt.next_property_offset(107, QUIET_NOTFOUND)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)

        # Check that NOTFOUND can be quietened.
        node = self.fdt.path_offset('/subnode@1/ss1')
        self.assertEqual(self.fdt.first_property_offset(node, QUIET_NOTFOUND),
                          -libfdt.NOTFOUND)
        with self.assertRaises(FdtException) as e:
            self.fdt.first_property_offset(node)
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)

    def testGetName(self):
        """Check that we can get the name of a node"""
        self.assertEqual(self.fdt.get_name(0), '')
        node = self.fdt.path_offset('/subnode@1/subsubnode')
        self.assertEqual(self.fdt.get_name(node), 'subsubnode')

        with self.assertRaises(FdtException) as e:
            self.fdt.get_name(-2)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)

    def testGetPropertyByOffset(self):
        """Check that we can read the name and contents of a property"""
        root = 0
        poffset = self.fdt.first_property_offset(root)
        prop = self.fdt.get_property_by_offset(poffset)
        self.assertEqual(prop.name, 'compatible')
        self.assertEqual(prop, b'test_tree1\0')

        with self.assertRaises(FdtException) as e:
            self.fdt.get_property_by_offset(-2)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)
        self.assertEqual(
                -libfdt.BADOFFSET,
                self.fdt.get_property_by_offset(-2, [libfdt.BADOFFSET]))

    def testGetProp(self):
        """Check that we can read the contents of a property by name"""
        root = self.fdt.path_offset('/')
        value = self.fdt.getprop(root, "compatible")
        self.assertEqual(value, b'test_tree1\0')
        self.assertEqual(-libfdt.NOTFOUND, self.fdt.getprop(root, 'missing',
                                                             QUIET_NOTFOUND))

        with self.assertRaises(FdtException) as e:
            self.fdt.getprop(root, 'missing')
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)

        node = self.fdt.path_offset('/subnode@1/subsubnode')
        value = self.fdt.getprop(node, "compatible")
        self.assertEqual(value, b'subsubnode1\0subsubnode\0')

    def testStrError(self):
        """Check that we can get an error string"""
        self.assertEqual(libfdt.strerror(-libfdt.NOTFOUND),
                          'FDT_ERR_NOTFOUND')

    def testNextNodeOffset(self):
        """Check that we can walk through nodes"""
        node_list = []
        node = 0
        depth = 0
        while depth >= 0:
            node_list.append([depth, self.fdt.get_name(node)])
            node, depth = self.fdt.next_node(node, depth, (libfdt.BADOFFSET,))
        self.assertEqual(node_list, [
            [0, ''],
            [1, 'subnode@1'],
            [2, 'subsubnode'],
            [2, 'ss1'],
            [1, 'subnode@2'],
            [2, 'subsubnode@0'],
            [2, 'ss2'],
            ])

    def testFirstNextSubnodeOffset(self):
        """Check that we can walk through subnodes"""
        node_list = []
        node = self.fdt.first_subnode(0, QUIET_NOTFOUND)
        while node >= 0:
            node_list.append(self.fdt.get_name(node))
            node = self.fdt.next_subnode(node, QUIET_NOTFOUND)
        self.assertEqual(node_list, ['subnode@1', 'subnode@2'])

    def testFirstNextSubnodeOffsetExceptions(self):
        """Check except handling for first/next subnode functions"""
        node = self.fdt.path_offset('/subnode@1/subsubnode', QUIET_NOTFOUND)
        self.assertEqual(self.fdt.first_subnode(node, QUIET_NOTFOUND),
                          -libfdt.NOTFOUND)
        with self.assertRaises(FdtException) as e:
            self.fdt.first_subnode(node)
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)

        node = self.fdt.path_offset('/subnode@1/ss1', QUIET_NOTFOUND)
        self.assertEqual(self.fdt.next_subnode(node, QUIET_NOTFOUND),
                          -libfdt.NOTFOUND)
        with self.assertRaises(FdtException) as e:
            self.fdt.next_subnode(node)
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)

    def testDeleteProperty(self):
        """Test that we can delete a property"""
        node_name = '/subnode@1'
        self.assertEqual(self.GetPropList(node_name),
                          ['compatible', 'reg', 'prop-int'])
        node = self.fdt.path_offset('/%s' % node_name)
        self.assertEqual(self.fdt.delprop(node, 'reg'), 0)
        self.assertEqual(self.GetPropList(node_name),
                          ['compatible', 'prop-int'])

    def testHeader(self):
        """Test that we can access the header values"""
        self.assertEqual(self.fdt.magic(), 0xd00dfeed)
        self.assertEqual(self.fdt.totalsize(), len(self.fdt._fdt))
        self.assertEqual(self.fdt.off_dt_struct(), 88)
        self.assertEqual(self.fdt.off_dt_strings(), 652)
        self.assertEqual(self.fdt.off_mem_rsvmap(), 40)
        self.assertEqual(self.fdt.version(), 17)
        self.assertEqual(self.fdt.last_comp_version(), 16)
        self.assertEqual(self.fdt.boot_cpuid_phys(), 0)
        self.assertEqual(self.fdt.size_dt_strings(), 105)
        self.assertEqual(self.fdt.size_dt_struct(), 564)

    def testPack(self):
        """Test that we can pack the tree after deleting something"""
        orig_size = self.fdt.totalsize()
        node = self.fdt.path_offset('/subnode@2', QUIET_NOTFOUND)
        self.assertEqual(self.fdt.delprop(node, 'prop-int'), 0)
        self.assertEqual(orig_size, self.fdt.totalsize())
        self.assertEqual(self.fdt.pack(), 0)
        self.assertTrue(self.fdt.totalsize() < orig_size)
        self.assertEqual(self.fdt.totalsize(), len(self.fdt.as_bytearray()))

    def testBadPropertyOffset(self):
        """Test that bad property offsets are detected"""
        with self.assertRaises(FdtException) as e:
            self.fdt.get_property_by_offset(13)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)
        with self.assertRaises(FdtException) as e:
            self.fdt.first_property_offset(3)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)
        with self.assertRaises(FdtException) as e:
            self.fdt.next_property_offset(3)
        self.assertEqual(e.exception.err, -libfdt.BADOFFSET)

    def testBadPathOffset(self):
        """Test that bad path names are detected"""
        with self.assertRaisesRegex(FdtException, get_err(libfdt.BADPATH)):
            self.fdt.path_offset('not-present')

    def testQuietAll(self):
        """Check that exceptions can be masked by QUIET_ALL"""
        self.assertEqual(-libfdt.NOTFOUND,
                          self.fdt.path_offset('/missing', QUIET_ALL))
        self.assertEqual(-libfdt.BADOFFSET,
                          self.fdt.get_property_by_offset(13, QUIET_ALL))
        self.assertEqual(-libfdt.BADPATH,
                          self.fdt.path_offset('missing', QUIET_ALL))

    def testIntegers(self):
        """Check that integers can be passed and returned"""
        self.assertEqual(0, libfdt.fdt_get_phandle(self.fdt._fdt, 0))
        node2 = self.fdt.path_offset('/subnode@2')
        self.assertEqual(0x2000, libfdt.fdt_get_phandle(self.fdt._fdt, node2))

    def testGetPhandle(self):
        """Test for the get_phandle() method"""
        self.assertEqual(0, self.fdt.get_phandle(0))
        node2 = self.fdt.path_offset('/subnode@2')
        self.assertEqual(0x2000, self.fdt.get_phandle(node2))

    def testGetAlias(self):
        """Test for the get_alias() method"""
        self.assertEqual("/subnode@1", self.fdt3.get_alias('s1'))
        self.assertEqual("/subnode@1/subsubnode", self.fdt3.get_alias('ss1'))
        self.assertEqual("/subnode@1/subsubnode/subsubsubnode", self.fdt3.get_alias('sss1'))

    def testParentOffset(self):
        """Test for the parent_offset() method"""
        self.assertEqual(-libfdt.NOTFOUND,
                          self.fdt.parent_offset(0, QUIET_NOTFOUND))
        with self.assertRaises(FdtException) as e:
            self.fdt.parent_offset(0)
        self.assertEqual(e.exception.err, -libfdt.NOTFOUND)

        node1 = self.fdt.path_offset('/subnode@2')
        self.assertEqual(0, self.fdt.parent_offset(node1))
        node2 = self.fdt.path_offset('/subnode@2/subsubnode@0')
        self.assertEqual(node1, self.fdt.parent_offset(node2))

    def testNodeOffsetByPhandle(self):
        """Test for the node_offset_by_phandle() method"""
        self.assertEqual(-libfdt.NOTFOUND,
                          self.fdt.node_offset_by_phandle(1, QUIET_NOTFOUND))
        node1 = self.fdt.path_offset('/subnode@2')
        self.assertEqual(node1, self.fdt.node_offset_by_phandle(0x2000))
        node2 = self.fdt.path_offset('/subnode@2/subsubnode@0')
        self.assertEqual(node2, self.fdt.node_offset_by_phandle(0x2001))

    def get_prop(self, name):
        return self.fdt2.getprop(0, name)

    def testGetIntProperties(self):
        """Test that we can access properties as integers"""
        self.assertEqual(0xdeadbeef, self.get_prop("prop-hex32").as_uint32())
        self.assertEqual(123, self.get_prop("prop-uint32").as_uint32())
        self.assertEqual(-2, self.get_prop("prop-int32").as_int32())
        self.assertEqual(9223372036854775807,
                          self.get_prop("prop-uint64").as_uint64())
        self.assertEqual(-2, self.get_prop("prop-int64").as_int64())

    def testGetIntListProperties(self):
        """Test that we can access properties as integer lists"""
        self.assertEqual([128, -16, -2],
                         self.get_prop("prop-int32-array").as_int32_list())
        self.assertEqual([0x1, 0x98765432, 0xdeadbeef],
                         self.get_prop("prop-uint32-array").as_uint32_list())
        self.assertEqual([0x100000000, -2],
                         self.get_prop("prop-int64-array").as_int64_list())
        self.assertEqual([0x100000000, 0x1],
                         self.get_prop("prop-uint64-array").as_uint64_list())

    def testGetStringlistProperties(self):
        """Test that we can access properties as string list"""
        node = self.fdt.path_offset('/subnode@1/subsubnode')
        self.assertEqual(["subsubnode1", "subsubnode"],
                         self.fdt.getprop(node, "compatible").as_stringlist())
        self.assertEqual(["this is a placeholder string", "string2"],
                         self.fdt.getprop(node, "placeholder").as_stringlist())

    def testReserveMap(self):
        """Test that we can access the memory reserve map"""
        self.assertEqual(2, self.fdt.num_mem_rsv())
        self.assertEqual([ 0xdeadbeef00000000, 0x100000],
                          self.fdt.get_mem_rsv(0))
        self.assertEqual([123456789, 0o10000], self.fdt.get_mem_rsv(1))

    def testEmpty(self):
        """Test that we can create an empty tree"""
        self.assertEqual(-libfdt.NOSPACE,
                          Fdt.create_empty_tree(1, (libfdt.NOSPACE,)))
        fdt = Fdt.create_empty_tree(128)
        self.assertEqual(128, fdt.totalsize())

    def testOpenInto(self):
        """Test that we can resize a tree"""
        fdt = Fdt.create_empty_tree(128)
        self.assertEqual(128, fdt.totalsize())
        fdt.resize(256)
        self.assertEqual(256, fdt.totalsize())
        fdt.pack()
        self.assertTrue(fdt.totalsize() < 128)

    def testSetProp(self):
        """Test that we can update and create properties"""
        node = self.fdt.path_offset('/subnode@1')
        self.fdt.setprop(node, 'compatible', TEST_BYTES_1)
        self.assertEqual(TEST_BYTES_1, self.fdt.getprop(node, 'compatible'))

        # Check that this property is missing, and that we don't have space to
        # add it
        self.assertEqual(-libfdt.NOTFOUND,
                          self.fdt.getprop(node, 'missing', QUIET_NOTFOUND))
        self.assertEqual(-libfdt.NOSPACE,
                          self.fdt.setprop(node, 'missing', TEST_BYTES_1,
                                           quiet=(libfdt.NOSPACE,)))

        # Expand the device tree so we now have room
        self.fdt.resize(self.fdt.totalsize() + 50)
        self.fdt.setprop(node, 'missing', TEST_BYTES_1)
        self.assertEqual(TEST_BYTES_1, self.fdt.getprop(node, 'missing'))

    def testSetPropU32(self):
        """Test that we can update and create integer properties"""
        node = 0
        prop = 'prop-int'
        self.fdt.setprop_u32(node, prop, TEST_VALUE_1)
        self.assertEqual(struct.pack('>I', TEST_VALUE_1),
                          self.fdt.getprop(node, prop))

    def testSetPropU64(self):
        """Test that we can update and create integer properties"""
        node = 0
        prop = 'prop-int64'
        self.fdt.setprop_u64(node, prop, TEST_VALUE64_1)
        self.assertEqual(struct.pack('>Q', TEST_VALUE64_1),
                          self.fdt.getprop(node, prop))

    def testSetPropStr(self):
        """Test that we can set a property to a particular string"""
        node = 0
        prop = 'prop-str'
        self.assertEqual(TEST_STRING_1, self.fdt.getprop(node, prop).as_str())
        self.fdt.setprop_str(node, prop, TEST_STRING_2)
        self.assertEqual(TEST_STRING_2, self.fdt.getprop(node, prop).as_str())
        with self.assertRaises(ValueError) as e:
            self.fdt.getprop(node, 'prop-int').as_str()
        self.assertIn('lacks nul termination', str(e.exception))

        node2 = self.fdt.path_offset('/subnode@1/subsubnode')
        with self.assertRaises(ValueError) as e:
            self.fdt.getprop(node2, 'compatible').as_str()
        self.assertIn('embedded nul', str(e.exception))

        # Expand the device tree so we now have room
        self.fdt.resize(self.fdt.totalsize() + 50)
        prop = 'prop-unicode'
        self.fdt.setprop_str(node, prop, TEST_STRING_3)
        self.assertEqual(TEST_STRING_3,
                          self.fdt.getprop(node, prop).as_str())

    def testSetName(self):
        """Test that we can update a node name"""
        node = self.fdt.path_offset('/subnode@1')
        old_val = self.fdt.get_name(node)
        self.fdt.set_name(node, 'test')
        self.assertEqual('test', self.fdt.get_name(node))

        with self.assertRaises(ValueError) as e:
            self.fdt.set_name(node, 'some\0name')
        self.assertIn('embedded nul', str(e.exception))

        with self.assertRaises(ValueError) as e:
            self.fdt.set_name(node, 'name\0')
        self.assertIn('embedded nul', str(e.exception))

    def testAddDeleteNodes(self):
        """Test that we can add and delete nodes"""
        node_name = '/subnode@1'
        self.assertEqual(self.GetSubnodes(node_name), ['subsubnode', 'ss1'])
        node = self.fdt.path_offset('%s/subsubnode' % node_name)
        self.assertEqual(self.fdt.del_node(node, 'subsubnode'), 0)
        self.assertEqual(self.GetSubnodes(node_name), ['ss1'])

        node = self.fdt.path_offset(node_name)
        offset = self.fdt.add_subnode(node, 'more')
        self.assertTrue(offset > 0)
        self.assertEqual(self.GetSubnodes(node_name), ['more', 'ss1'])


class PyLibfdtSwTests(unittest.TestCase):
    """Test class for pylibfdt sequential-write DT creation
    """
    def assertOk(self, err_code):
        self.assertEqual(0, err_code)

    def testCreate(self):
        # First check the minimum size and also the FdtSw() constructor
        with self.assertRaisesRegex(FdtException, get_err(libfdt.NOSPACE)):
            self.assertEqual(-libfdt.NOSPACE, FdtSw(3))

        sw = FdtSw()
        sw.add_reservemap_entry(TEST_ADDR_1, TEST_SIZE_1)
        sw.add_reservemap_entry(TEST_ADDR_2, TEST_SIZE_2)
        sw.finish_reservemap()

        sw.begin_node('')
        sw.property_string('compatible', 'test_tree1')
        sw.property_u32('prop-int', TEST_VALUE_1)

        sw.property_u32('prop-int', TEST_VALUE_1)
        sw.property_u64('prop-int64', TEST_VALUE64_1)
        sw.property_string('prop-str', TEST_STRING_1)
        sw.property_u32('#address-cells', 1)
        sw.property_u32('#size-cells', 0)

        sw.begin_node('subnode@1')
        sw.property_string('compatible', 'subnode1')
        sw.property_u32('reg', 1)
        sw.property_cell('prop-int', TEST_VALUE_1)
        sw.property('data', b'\x00data\x01')
        sw.begin_node('subsubnode')
        sw.property('compatible', b'subsubnode1\0subsubnode')
        sw.property_cell('prop-int', TEST_VALUE_1)
        sw.end_node()
        sw.begin_node('ss1')
        sw.end_node()
        sw.end_node()

        for i in range(2, 11):
            with sw.add_node('subnode@%d' % i):
                sw.property_u32('reg', 2)
                sw.property_cell('linux,phandle', PHANDLE_1)
                sw.property_cell('prop-int', TEST_VALUE_2)
                sw.property_u32('#address-cells', 1)
                sw.property_u32('#size-cells', 0)
                with sw.add_node('subsubnode@0'):
                    sw.property_u32('reg', 0)
                    sw.property_cell('phandle', PHANDLE_2)
                    sw.property('compatible', b'subsubnode2\0subsubnode')
                    sw.property_cell('prop-int', TEST_VALUE_2)
                with sw.add_node('ss2'):
                    pass
        sw.end_node()

        fdt = sw.as_fdt()
        self.assertEqual(2, fdt.num_mem_rsv())
        self.assertEqual([TEST_ADDR_1, TEST_SIZE_1], fdt.get_mem_rsv(0))

        # Make sure we can add a few more things
        with sw.add_node('another'):
            sw.property_u32('reg', 3)

        # Make sure we can read from the tree too
        node = sw.path_offset('/subnode@1')
        self.assertEqual(b'subnode1\0', sw.getprop(node, 'compatible'))

        # Make sure we did at least two resizes
        self.assertTrue(len(fdt.as_bytearray()) > FdtSw.INC_SIZE * 2)


class PyLibfdtRoTests(unittest.TestCase):
    """Test class for read-only pylibfdt access functions

    This just tests a few simple cases. Most of the tests are in
    PyLibfdtBasicTests.

    Properties:
        fdt: Device tree file used for testing
    """

    def setUp(self):
        """Read in the device tree we use for testing"""
        with open('test_tree1.dtb', mode='rb') as f:
            self.fdt = libfdt.FdtRo(f.read())

    def testAccess(self):
        """Basic sanity check for the FdtRo class"""
        node = self.fdt.path_offset('/subnode@1')
        self.assertEqual(b'subnode1\0',
                         self.fdt.getprop(node, 'compatible'))
        node = self.fdt.first_subnode(node)
        self.assertEqual(b'this is a placeholder string\0string2\0',
                         self.fdt.getprop(node, 'placeholder'))


if __name__ == "__main__":
    unittest.main()
