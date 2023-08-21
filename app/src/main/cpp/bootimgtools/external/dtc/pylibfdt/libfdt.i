// SPDX-License-Identifier: (GPL-2.0-or-later OR BSD-2-Clause)
/*
 * pylibfdt - Flat Device Tree manipulation in Python
 * Copyright (C) 2017 Google, Inc.
 * Written by Simon Glass <sjg@chromium.org>
 */

%module libfdt

%include <stdint.i>

%{
#define SWIG_FILE_WITH_INIT
#include "libfdt.h"

/*
 * We rename this function here to avoid problems with swig, since we also have
 * a struct called fdt_property. That struct causes swig to create a class in
 * libfdt.py called fdt_property(), which confuses things.
 */
static int fdt_property_stub(void *fdt, const char *name, const void *val,
                             int len)
{
    return fdt_property(fdt, name, val, len);
}

%}

%pythoncode %{

import struct

# Error codes, corresponding to FDT_ERR_... in libfdt.h
(NOTFOUND,
        EXISTS,
        NOSPACE,
        BADOFFSET,
        BADPATH,
        BADPHANDLE,
        BADSTATE,
        TRUNCATED,
        BADMAGIC,
        BADVERSION,
        BADSTRUCTURE,
        BADLAYOUT,
        INTERNAL,
        BADNCELLS,
        BADVALUE,
        BADOVERLAY,
        NOPHANDLES) = QUIET_ALL = range(1, 18)
# QUIET_ALL can be passed as the 'quiet' parameter to avoid exceptions
# altogether. All # functions passed this value will return an error instead
# of raising an exception.

# Pass this as the 'quiet' parameter to return -ENOTFOUND on NOTFOUND errors,
# instead of raising an exception.
QUIET_NOTFOUND = (NOTFOUND,)
QUIET_NOSPACE = (NOSPACE,)


class FdtException(Exception):
    """An exception caused by an error such as one of the codes above"""
    def __init__(self, err):
        self.err = err

    def __str__(self):
        return 'pylibfdt error %d: %s' % (self.err, fdt_strerror(self.err))

def strerror(fdt_err):
    """Get the string for an error number

    Args:
        fdt_err: Error number (-ve)

    Returns:
        String containing the associated error
    """
    return fdt_strerror(fdt_err)

def check_err(val, quiet=()):
    """Raise an error if the return value is -ve

    This is used to check for errors returned by libfdt C functions.

    Args:
        val: Return value from a libfdt function
        quiet: Errors to ignore (empty to raise on all errors)

    Returns:
        val if val >= 0

    Raises
        FdtException if val < 0
    """
    if isinstance(val, int) and val < 0:
        if -val not in quiet:
            raise FdtException(val)
    return val

def check_err_null(val, quiet=()):
    """Raise an error if the return value is NULL

    This is used to check for a NULL return value from certain libfdt C
    functions

    Args:
        val: Return value from a libfdt function
        quiet: Errors to ignore (empty to raise on all errors)

    Returns:
        val if val is a list, None if not

    Raises
        FdtException if val indicates an error was reported and the error
        is not in @quiet.
    """
    # Normally a list is returned which contains the data and its length.
    # If we get just an integer error code, it means the function failed.
    if not isinstance(val, list):
        if -val not in quiet:
            raise FdtException(val)
    return val

class FdtRo(object):
    """Class for a read-only device-tree

    This is a base class used by FdtRw (read-write access) and FdtSw
    (sequential-write access). It implements read-only access to the
    device tree.

    Here are the three classes and when you should use them:

        FdtRo - read-only access to an existing FDT
        FdtRw - read-write access to an existing FDT (most common case)
        FdtSw - for creating a new FDT, as well as allowing read-only access
    """
    def __init__(self, data):
        self._fdt = bytearray(data)
        check_err(fdt_check_header(self._fdt));

    def as_bytearray(self):
        """Get the device tree contents as a bytearray

        This can be passed directly to libfdt functions that access a
        const void * for the device tree.

        Returns:
            bytearray containing the device tree
        """
        return bytearray(self._fdt)

    def next_node(self, nodeoffset, depth, quiet=()):
        """Find the next subnode

        Args:
            nodeoffset: Node offset of previous node
            depth: The depth of the node at nodeoffset. This is used to
                calculate the depth of the returned node
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Typle:
                Offset of the next node, if any, else a -ve error
                Depth of the returned node, if any, else undefined

        Raises:
            FdtException if no more nodes found or other error occurs
        """
        return check_err(fdt_next_node(self._fdt, nodeoffset, depth), quiet)

    def first_subnode(self, nodeoffset, quiet=()):
        """Find the first subnode of a parent node

        Args:
            nodeoffset: Node offset of parent node
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            The offset of the first subnode, if any

        Raises:
            FdtException if no subnodes found or other error occurs
        """
        return check_err(fdt_first_subnode(self._fdt, nodeoffset), quiet)

    def next_subnode(self, nodeoffset, quiet=()):
        """Find the next subnode

        Args:
            nodeoffset: Node offset of previous subnode
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            The offset of the next subnode, if any

        Raises:
            FdtException if no more subnodes found or other error occurs
        """
        return check_err(fdt_next_subnode(self._fdt, nodeoffset), quiet)

    def magic(self):
        """Return the magic word from the header

        Returns:
            Magic word
        """
        return fdt_magic(self._fdt)

    def totalsize(self):
        """Return the total size of the device tree

        Returns:
            Total tree size in bytes
        """
        return fdt_totalsize(self._fdt)

    def off_dt_struct(self):
        """Return the start of the device-tree struct area

        Returns:
            Start offset of struct area
        """
        return fdt_off_dt_struct(self._fdt)

    def off_dt_strings(self):
        """Return the start of the device-tree string area

        Returns:
            Start offset of string area
        """
        return fdt_off_dt_strings(self._fdt)

    def off_mem_rsvmap(self):
        """Return the start of the memory reserve map

        Returns:
            Start offset of memory reserve map
        """
        return fdt_off_mem_rsvmap(self._fdt)

    def version(self):
        """Return the version of the device tree

        Returns:
            Version number of the device tree
        """
        return fdt_version(self._fdt)

    def last_comp_version(self):
        """Return the last compatible version of the device tree

        Returns:
            Last compatible version number of the device tree
        """
        return fdt_last_comp_version(self._fdt)

    def boot_cpuid_phys(self):
        """Return the physical boot CPU ID

        Returns:
            Physical boot CPU ID
        """
        return fdt_boot_cpuid_phys(self._fdt)

    def size_dt_strings(self):
        """Return the start of the device-tree string area

        Returns:
            Start offset of string area
        """
        return fdt_size_dt_strings(self._fdt)

    def size_dt_struct(self):
        """Return the start of the device-tree struct area

        Returns:
            Start offset of struct area
        """
        return fdt_size_dt_struct(self._fdt)

    def num_mem_rsv(self, quiet=()):
        """Return the number of memory reserve-map records

        Returns:
            Number of memory reserve-map records
        """
        return check_err(fdt_num_mem_rsv(self._fdt), quiet)

    def get_mem_rsv(self, index, quiet=()):
        """Return the indexed memory reserve-map record

        Args:
            index: Record to return (0=first)

        Returns:
            Number of memory reserve-map records
        """
        return check_err(fdt_get_mem_rsv(self._fdt, index), quiet)

    def subnode_offset(self, parentoffset, name, quiet=()):
        """Get the offset of a named subnode

        Args:
            parentoffset: Offset of the parent node to check
            name: Name of the required subnode, e.g. 'subnode@1'
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            The node offset of the found node, if any

        Raises
            FdtException if there is no node with that name, or other error
        """
        return check_err(fdt_subnode_offset(self._fdt, parentoffset, name),
                         quiet)

    def path_offset(self, path, quiet=()):
        """Get the offset for a given path

        Args:
            path: Path to the required node, e.g. '/node@3/subnode@1'
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Node offset

        Raises
            FdtException if the path is not valid or not found
        """
        return check_err(fdt_path_offset(self._fdt, path), quiet)

    def get_name(self, nodeoffset):
        """Get the name of a node

        Args:
            nodeoffset: Offset of node to check

        Returns:
            Node name

        Raises:
            FdtException on error (e.g. nodeoffset is invalid)
        """
        return check_err_null(fdt_get_name(self._fdt, nodeoffset))[0]

    def first_property_offset(self, nodeoffset, quiet=()):
        """Get the offset of the first property in a node offset

        Args:
            nodeoffset: Offset to the node to check
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Offset of the first property

        Raises
            FdtException if the associated node has no properties, or some
                other error occurred
        """
        return check_err(fdt_first_property_offset(self._fdt, nodeoffset),
                         quiet)

    def next_property_offset(self, prop_offset, quiet=()):
        """Get the next property in a node

        Args:
            prop_offset: Offset of the previous property
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Offset of the next property

        Raises:
            FdtException if the associated node has no more properties, or
                some other error occurred
        """
        return check_err(fdt_next_property_offset(self._fdt, prop_offset),
                         quiet)

    def get_property_by_offset(self, prop_offset, quiet=()):
        """Obtains a property that can be examined

        Args:
            prop_offset: Offset of property (e.g. from first_property_offset())
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Property object, or None if not found

        Raises:
            FdtException on error (e.g. invalid prop_offset or device
            tree format)
        """
        pdata = check_err_null(
                fdt_get_property_by_offset(self._fdt, prop_offset), quiet)
        if isinstance(pdata, (int)):
            return pdata
        return Property(pdata[0], pdata[1])

    def getprop(self, nodeoffset, prop_name, quiet=()):
        """Get a property from a node

        Args:
            nodeoffset: Node offset containing property to get
            prop_name: Name of property to get
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Value of property as a Property object (which can be used as a
               bytearray/string), or -ve error number. On failure, returns an
               integer error

        Raises:
            FdtError if any error occurs (e.g. the property is not found)
        """
        pdata = check_err_null(fdt_getprop(self._fdt, nodeoffset, prop_name),
                               quiet)
        if isinstance(pdata, (int)):
            return pdata
        return Property(prop_name, bytearray(pdata[0]))

    def get_phandle(self, nodeoffset):
        """Get the phandle of a node

        Args:
            nodeoffset: Node offset to check

        Returns:
            phandle of node, or 0 if the node has no phandle or another error
            occurs
        """
        return fdt_get_phandle(self._fdt, nodeoffset)

    def get_alias(self, name):
        """Get the full path referenced by a given alias

        Args:
            name: name of the alias to lookup

        Returns:
            Full path to the node for the alias named 'name', if it exists
            None, if the given alias or the /aliases node does not exist
        """
        return fdt_get_alias(self._fdt, name)

    def parent_offset(self, nodeoffset, quiet=()):
        """Get the offset of a node's parent

        Args:
            nodeoffset: Node offset to check
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            The offset of the parent node, if any

        Raises:
            FdtException if no parent found or other error occurs
        """
        return check_err(fdt_parent_offset(self._fdt, nodeoffset), quiet)

    def node_offset_by_phandle(self, phandle, quiet=()):
        """Get the offset of a node with the given phandle

        Args:
            phandle: Phandle to search for
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            The offset of node with that phandle, if any

        Raises:
            FdtException if no node found or other error occurs
        """
        return check_err(fdt_node_offset_by_phandle(self._fdt, phandle), quiet)


class Fdt(FdtRo):
    """Device tree class, supporting all operations

    The Fdt object is created is created from a device tree binary file,
    e.g. with something like:

       fdt = Fdt(open("filename.dtb").read())

    Operations can then be performed using the methods in this class. Each
    method xxx(args...) corresponds to a libfdt function fdt_xxx(fdt, args...).

    All methods raise an FdtException if an error occurs. To avoid this
    behaviour a 'quiet' parameter is provided for some functions. This
    defaults to empty, but you can pass a list of errors that you expect.
    If one of these errors occurs, the function will return an error number
    (e.g. -NOTFOUND).
    """
    def __init__(self, data):
        FdtRo.__init__(self, data)

    @staticmethod
    def create_empty_tree(size, quiet=()):
        """Create an empty device tree ready for use

        Args:
            size: Size of device tree in bytes

        Returns:
            Fdt object containing the device tree
        """
        data = bytearray(size)
        err = check_err(fdt_create_empty_tree(data, size), quiet)
        if err:
            return err
        return Fdt(data)

    def resize(self, size, quiet=()):
        """Move the device tree into a larger or smaller space

        This creates a new device tree of size @size and moves the existing
        device tree contents over to that. It can be used to create more space
        in a device tree. Note that the Fdt object remains the same, but it
        now has a new bytearray holding the contents.

        Args:
            size: Required new size of device tree in bytes
        """
        fdt = bytearray(size)
        err = check_err(fdt_open_into(self._fdt, fdt, size), quiet)
        if err:
            return err
        self._fdt = fdt

    def pack(self, quiet=()):
        """Pack the device tree to remove unused space

        This adjusts the tree in place.

        Args:
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtException if any error occurs
        """
        err = check_err(fdt_pack(self._fdt), quiet)
        if err:
            return err
        del self._fdt[self.totalsize():]
        return err

    def set_name(self, nodeoffset, name, quiet=()):
        """Set the name of a node

        Args:
            nodeoffset: Node offset of node to update
            name: New node name (string without \0)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtException if no parent found or other error occurs
        """
        if chr(0) in name:
            raise ValueError('Property contains embedded nul characters')
        return check_err(fdt_set_name(self._fdt, nodeoffset, name), quiet)

    def setprop(self, nodeoffset, prop_name, val, quiet=()):
        """Set the value of a property

        Args:
            nodeoffset: Node offset containing the property to create/update
            prop_name: Name of property
            val: Value to write (string or bytearray)
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtException if no parent found or other error occurs
        """
        return check_err(fdt_setprop(self._fdt, nodeoffset, prop_name, val,
                                     len(val)), quiet)

    def setprop_u32(self, nodeoffset, prop_name, val, quiet=()):
        """Set the value of a property

        Args:
            nodeoffset: Node offset containing the property to create/update
            prop_name: Name of property
            val: Value to write (integer)
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtException if no parent found or other error occurs
        """
        return check_err(fdt_setprop_u32(self._fdt, nodeoffset, prop_name, val),
                         quiet)

    def setprop_u64(self, nodeoffset, prop_name, val, quiet=()):
        """Set the value of a property

        Args:
            nodeoffset: Node offset containing the property to create/update
            prop_name: Name of property
            val: Value to write (integer)
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtException if no parent found or other error occurs
        """
        return check_err(fdt_setprop_u64(self._fdt, nodeoffset, prop_name, val),
                         quiet)

    def setprop_str(self, nodeoffset, prop_name, val, quiet=()):
        """Set the string value of a property

        The property is set to the string, with a nul terminator added

        Args:
            nodeoffset: Node offset containing the property to create/update
            prop_name: Name of property
            val: Value to write (string without nul terminator). Unicode is
                supposed by encoding to UTF-8
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtException if no parent found or other error occurs
        """
        val = val.encode('utf-8') + b'\0'
        return check_err(fdt_setprop(self._fdt, nodeoffset, prop_name,
                                     val, len(val)), quiet)

    def delprop(self, nodeoffset, prop_name, quiet=()):
        """Delete a property from a node

        Args:
            nodeoffset: Node offset containing property to delete
            prop_name: Name of property to delete
            quiet: Errors to ignore (empty to raise on all errors)

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtError if the property does not exist, or another error occurs
        """
        return check_err(fdt_delprop(self._fdt, nodeoffset, prop_name), quiet)

    def add_subnode(self, parentoffset, name, quiet=()):
        """Add a new subnode to a node

        Args:
            parentoffset: Parent offset to add the subnode to
            name: Name of node to add

        Returns:
            offset of the node created, or negative error code on failure

        Raises:
            FdtError if there is not enough space, or another error occurs
        """
        return check_err(fdt_add_subnode(self._fdt, parentoffset, name), quiet)

    def del_node(self, nodeoffset, quiet=()):
        """Delete a node

        Args:
            nodeoffset: Offset of node to delete

        Returns:
            Error code, or 0 if OK

        Raises:
            FdtError if an error occurs
        """
        return check_err(fdt_del_node(self._fdt, nodeoffset), quiet)


class Property(bytearray):
    """Holds a device tree property name and value.

    This holds a copy of a property taken from the device tree. It does not
    reference the device tree, so if anything changes in the device tree,
    a Property object will remain valid.

    Properties:
        name: Property name
        value: Property value as a bytearray
    """
    def __init__(self, name, value):
        bytearray.__init__(self, value)
        self.name = name

    def as_cell(self, fmt):
        return struct.unpack('>' + fmt, self)[0]

    def as_uint32(self):
        return self.as_cell('L')

    def as_int32(self):
        return self.as_cell('l')

    def as_uint64(self):
        return self.as_cell('Q')

    def as_int64(self):
        return self.as_cell('q')

    def as_list(self, fmt):
        return list(map(lambda x: x[0], struct.iter_unpack('>' + fmt, self)))

    def as_uint32_list(self):
        return self.as_list('L')

    def as_int32_list(self):
        return self.as_list('l')

    def as_uint64_list(self):
        return self.as_list('Q')

    def as_int64_list(self):
        return self.as_list('q')

    def as_str(self):
        """Unicode is supported by decoding from UTF-8"""
        if self[-1] != 0:
            raise ValueError('Property lacks nul termination')
        if 0 in self[:-1]:
            raise ValueError('Property contains embedded nul characters')
        return self[:-1].decode('utf-8')

    def as_stringlist(self):
        """Unicode is supported by decoding from UTF-8"""
        if self[-1] != 0:
            raise ValueError('Property lacks nul termination')
        parts = self[:-1].split(b'\x00')
        return list(map(lambda x: x.decode('utf-8'), parts))


class FdtSw(FdtRo):
    """Software interface to create a device tree from scratch

    The methods in this class work by adding to an existing 'partial' device
    tree buffer of a fixed size created by instantiating this class. When the
    tree is complete, call as_fdt() to obtain a device tree ready to be used.

    Similarly with nodes, a new node is started with begin_node() and finished
    with end_node().

    The context manager functions can be used to make this a bit easier:

    # First create the device tree with a node and property:
    sw = FdtSw()
    sw.finish_reservemap()
    with sw.add_node(''):
        with sw.add_node('node'):
            sw.property_u32('reg', 2)
    fdt = sw.as_fdt()

    # Now we can use it as a real device tree
    fdt.setprop_u32(0, 'reg', 3)

    The size hint provides a starting size for the space to be used by the
    device tree. This will be increased automatically as needed as new items
    are added to the tree.
    """
    INC_SIZE = 1024  # Expand size by this much when out of space

    def __init__(self, size_hint=None):
        """Create a new FdtSw object

        Args:
            size_hint: A hint as to the initial size to use

        Raises:
            ValueError if size_hint is negative

        Returns:
            FdtSw object on success, else integer error code (if not raising)
        """
        if not size_hint:
            size_hint = self.INC_SIZE
        fdtsw = bytearray(size_hint)
        err = check_err(fdt_create(fdtsw, size_hint))
        if err:
            return err
        self._fdt = fdtsw

    def as_fdt(self):
        """Convert a FdtSw into an Fdt so it can be accessed as normal

        Creates a new Fdt object from the work-in-progress device tree. This
        does not call fdt_finish() on the current object, so it is possible to
        add more nodes/properties and call as_fdt() again to get an updated
        tree.

        Returns:
            Fdt object allowing access to the newly created device tree
        """
        fdtsw = bytearray(self._fdt)
        check_err(fdt_finish(fdtsw))
        return Fdt(fdtsw)

    def check_space(self, val):
        """Check if we need to add more space to the FDT

        This should be called with the error code from an operation. If this is
        -NOSPACE then the FDT will be expanded to have more space, and True will
        be returned, indicating that the operation needs to be tried again.

        Args:
            val: Return value from the operation that was attempted

        Returns:
            True if the operation must be retried, else False
        """
        if check_err(val, QUIET_NOSPACE) < 0:
            self.resize(len(self._fdt) + self.INC_SIZE)
            return True
        return False

    def resize(self, size):
        """Resize the buffer to accommodate a larger tree

        Args:
            size: New size of tree

        Raises:
            FdtException on any error
        """
        fdt = bytearray(size)
        err = check_err(fdt_resize(self._fdt, fdt, size))
        self._fdt = fdt

    def add_reservemap_entry(self, addr, size):
        """Add a new memory reserve map entry

        Once finished adding, you must call finish_reservemap().

        Args:
            addr: 64-bit start address
            size: 64-bit size

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_add_reservemap_entry(self._fdt, addr,
                                                        size)):
            pass

    def finish_reservemap(self):
        """Indicate that there are no more reserve map entries to add

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_finish_reservemap(self._fdt)):
            pass

    def begin_node(self, name):
        """Begin a new node

        Use this before adding properties to the node. Then call end_node() to
        finish it. You can also use the context manager as shown in the FdtSw
        class comment.

        Args:
            name: Name of node to begin

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_begin_node(self._fdt, name)):
            pass

    def property_string(self, name, string):
        """Add a property with a string value

        The string will be nul-terminated when written to the device tree

        Args:
            name: Name of property to add
            string: String value of property

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_property_string(self._fdt, name, string)):
            pass

    def property_u32(self, name, val):
        """Add a property with a 32-bit value

        Write a single-cell value to the device tree

        Args:
            name: Name of property to add
            val: Value of property

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_property_u32(self._fdt, name, val)):
            pass

    def property_u64(self, name, val):
        """Add a property with a 64-bit value

        Write a double-cell value to the device tree in big-endian format

        Args:
            name: Name of property to add
            val: Value of property

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_property_u64(self._fdt, name, val)):
            pass

    def property_cell(self, name, val):
        """Add a property with a single-cell value

        Write a single-cell value to the device tree

        Args:
            name: Name of property to add
            val: Value of property
            quiet: Errors to ignore (empty to raise on all errors)

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_property_cell(self._fdt, name, val)):
            pass

    def property(self, name, val):
        """Add a property

        Write a new property with the given value to the device tree. The value
        is taken as is and is not nul-terminated

        Args:
            name: Name of property to add
            val: Value of property (bytes)
            quiet: Errors to ignore (empty to raise on all errors)

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_property_stub(self._fdt, name, val,
                                                 len(val))):
            pass

    def end_node(self):
        """End a node

        Use this after adding properties to a node to close it off. You can also
        use the context manager as shown in the FdtSw class comment.

        Args:
            quiet: Errors to ignore (empty to raise on all errors)

        Raises:
            FdtException on any error
        """
        while self.check_space(fdt_end_node(self._fdt)):
            pass

    def add_node(self, name):
        """Create a new context for adding a node

        When used in a 'with' clause this starts a new node and finishes it
        afterward.

        Args:
            name: Name of node to add
        """
        return NodeAdder(self, name)


class NodeAdder():
    """Class to provide a node context

    This allows you to add nodes in a more natural way:

        with fdtsw.add_node('name'):
            fdtsw.property_string('test', 'value')

    The node is automatically completed with a call to end_node() when the
    context exits.
    """
    def __init__(self, fdtsw, name):
        self._fdt = fdtsw
        self._name = name

    def __enter__(self):
        self._fdt.begin_node(self._name)

    def __exit__(self, type, value, traceback):
        self._fdt.end_node()
%}

%rename(fdt_property) fdt_property_func;

/*
 * fdt32_t is a big-endian 32-bit value defined to uint32_t in libfdt_env.h
 * so use the same type here.
 */
typedef uint32_t fdt32_t;

%include "fdt.h"

%include "typemaps.i"

/* Most functions don't change the device tree, so use a const void * */
%typemap(in) (const void *)(const void *fdt) {
	if (!PyByteArray_Check($input)) {
		SWIG_exception_fail(SWIG_TypeError, "in method '" "$symname"
			"', argument " "$argnum"" of type '" "$type""'");
	}
	$1 = (void *)PyByteArray_AsString($input);
        fdt = $1;
        (void)fdt; /* avoid unused variable warning */
}

/* Some functions do change the device tree, so use void * */
%typemap(in) (void *)(const void *fdt) {
	if (!PyByteArray_Check($input)) {
		SWIG_exception_fail(SWIG_TypeError, "in method '" "$symname"
			"', argument " "$argnum"" of type '" "$type""'");
	}
	$1 = PyByteArray_AsString($input);
        fdt = $1;
        (void)fdt; /* avoid unused variable warning */
}

/* typemap used for fdt_get_property_by_offset() */
%typemap(out) (struct fdt_property *) {
	PyObject *buff;

	if ($1) {
		resultobj = PyString_FromString(
			fdt_string(fdt1, fdt32_to_cpu($1->nameoff)));
		buff = PyByteArray_FromStringAndSize(
			(const char *)($1 + 1), fdt32_to_cpu($1->len));
		resultobj = SWIG_Python_AppendOutput(resultobj, buff);
	}
}

%apply int *OUTPUT { int *lenp };

/* typemap used for fdt_getprop() */
%typemap(out) (const void *) {
	if (!$1) {
		$result = Py_None;
		Py_INCREF($result);
	} else {
        %#if PY_VERSION_HEX >= 0x03000000
            $result = Py_BuildValue("y#", $1, (Py_ssize_t)*arg4);
        %#else
            $result = Py_BuildValue("s#", $1, (Py_ssize_t)*arg4);
        %#endif
    }
}

/* typemap used for fdt_setprop() */
%typemap(in) (const void *val) {
    %#if PY_VERSION_HEX >= 0x03000000
        if (!PyBytes_Check($input)) {
            SWIG_exception_fail(SWIG_TypeError, "bytes expected in method '" "$symname"
                "', argument " "$argnum"" of type '" "$type""'");
        }
        $1 = PyBytes_AsString($input);
    %#else
        $1 = PyString_AsString($input);   /* char *str */
    %#endif
}

/* typemaps used for fdt_next_node() */
%typemap(in, numinputs=1) int *depth (int depth) {
   depth = (int) PyInt_AsLong($input);
   $1 = &depth;
}

%typemap(argout) int *depth {
        PyObject *val = Py_BuildValue("i", *arg$argnum);
        resultobj = SWIG_Python_AppendOutput(resultobj, val);
}

%apply int *depth { int *depth };

/* typemaps for fdt_get_mem_rsv */
%typemap(in, numinputs=0) uint64_t * (uint64_t temp) {
   $1 = &temp;
}

%typemap(argout) uint64_t * {
        PyObject *val = PyLong_FromUnsignedLongLong(*arg$argnum);
        if (!result) {
           if (PyTuple_GET_SIZE(resultobj) == 0)
              resultobj = val;
           else
              resultobj = SWIG_Python_AppendOutput(resultobj, val);
        }
}

/* We have both struct fdt_property and a function fdt_property() */
%warnfilter(302) fdt_property;

/* These are macros in the header so have to be redefined here */
uint32_t fdt_magic(const void *fdt);
uint32_t fdt_totalsize(const void *fdt);
uint32_t fdt_off_dt_struct(const void *fdt);
uint32_t fdt_off_dt_strings(const void *fdt);
uint32_t fdt_off_mem_rsvmap(const void *fdt);
uint32_t fdt_version(const void *fdt);
uint32_t fdt_last_comp_version(const void *fdt);
uint32_t fdt_boot_cpuid_phys(const void *fdt);
uint32_t fdt_size_dt_strings(const void *fdt);
uint32_t fdt_size_dt_struct(const void *fdt);

int fdt_property_string(void *fdt, const char *name, const char *val);
int fdt_property_cell(void *fdt, const char *name, uint32_t val);

/*
 * This function has a stub since the name fdt_property is used for both a
  * function and a struct, which confuses SWIG.
 */
int fdt_property_stub(void *fdt, const char *name, const void *val, int len);

%include <libfdt.h>
