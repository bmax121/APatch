///////////////////////////////////////////////////////////////////////////////
//
/// \file       tests.h
/// \brief      Common definitions for test applications
//
//  Author:     Lasse Collin
//
//  This file has been put into the public domain.
//  You can do whatever you want with this file.
//
///////////////////////////////////////////////////////////////////////////////

#ifndef LZMA_TESTS_H
#define LZMA_TESTS_H

#include "sysdefs.h"
#include "tuklib_integer.h"
#include "lzma.h"
#include "tuktest.h"


#define memcrap(buf, size) memset(buf, 0xFD, size)


// TODO: Remove these three macros once all tests have been converted.
#define expect(test) ((test) ? 0 : (fprintf(stderr, "%s:%d: %s\n", \
	__FILE__, __LINE__, #test), abort(), 0))

#define succeed(test) expect(!(test))

#define fail(test) expect(test)


// This table and macro allow getting more readable error messages when
// comparing the lzma_ret enumeration values.
static const char enum_strings_lzma_ret[][24] = {
	"LZMA_OK",
	"LZMA_STREAM_END",
	"LZMA_NO_CHECK",
	"LZMA_UNSUPPORTED_CHECK",
	"LZMA_GET_CHECK",
	"LZMA_MEM_ERROR",
	"LZMA_MEMLIMIT_ERROR",
	"LZMA_FORMAT_ERROR",
	"LZMA_OPTIONS_ERROR",
	"LZMA_DATA_ERROR",
	"LZMA_BUF_ERROR",
	"LZMA_PROG_ERROR",
	"LZMA_SEEK_NEEDED",
};

#define assert_lzma_ret(test_expr, ref_val) \
	assert_enum_eq(test_expr, ref_val, enum_strings_lzma_ret)


static const char enum_strings_lzma_check[][24] = {
	"LZMA_CHECK_NONE",
	"LZMA_CHECK_CRC32",
	"LZMA_CHECK_UNKNOWN_2",
	"LZMA_CHECK_UNKNOWN_3",
	"LZMA_CHECK_CRC64",
	"LZMA_CHECK_UNKNOWN_5",
	"LZMA_CHECK_UNKNOWN_6",
	"LZMA_CHECK_UNKNOWN_7",
	"LZMA_CHECK_UNKNOWN_8",
	"LZMA_CHECK_UNKNOWN_9",
	"LZMA_CHECK_SHA256",
	"LZMA_CHECK_UNKNOWN_11",
	"LZMA_CHECK_UNKNOWN_12",
	"LZMA_CHECK_UNKNOWN_13",
	"LZMA_CHECK_UNKNOWN_14",
	"LZMA_CHECK_UNKNOWN_15",
};

#define assert_lzma_check(test_expr, ref_val) \
	assert_enum_eq(test_expr, ref_val, enum_strings_lzma_check)


static inline bool
coder_loop(lzma_stream *strm, uint8_t *in, size_t in_size,
		uint8_t *out, size_t out_size,
		lzma_ret expected_ret, lzma_action finishing_action)
{
	size_t in_left = in_size;
	size_t out_left = out_size > 0 ? out_size + 1 : 0;
	lzma_action action = LZMA_RUN;
	lzma_ret ret;

	strm->next_in = NULL;
	strm->avail_in = 0;
	strm->next_out = NULL;
	strm->avail_out = 0;

	while (true) {
		if (in_left > 0) {
			if (--in_left == 0)
				action = finishing_action;

			strm->next_in = in++;
			strm->avail_in = 1;
		}

		if (out_left > 0) {
			--out_left;
			strm->next_out = out++;
			strm->avail_out = 1;
		}

		ret = lzma_code(strm, action);
		if (ret != LZMA_OK)
			break;
	}

	bool error = false;

	if (ret != expected_ret)
		error = true;

	if (strm->total_in != in_size || strm->total_out != out_size)
		error = true;

	return error;
}


static inline bool
decoder_loop_ret(lzma_stream *strm, uint8_t *in, size_t in_size,
		lzma_ret expected_ret)
{
	return coder_loop(strm, in, in_size, NULL, 0, expected_ret, LZMA_RUN);
}


static inline bool
decoder_loop(lzma_stream *strm, uint8_t *in, size_t in_size)
{
	return coder_loop(strm, in, in_size, NULL, 0,
			LZMA_STREAM_END, LZMA_RUN);
}

#endif
