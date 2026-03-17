// file: luajit/src/lovely_jni_bridge.cpp

#include <jni.h>
#include <stdlib.h>

extern "C" {
#include "lua.h"
#include "lauxlib.h"

typedef int (*luaL_loadbufferx_ptr)(lua_State *L, const char *buff, size_t sz,
		const char *name, const char *mode);
typedef void (*lua_call_ptr)(lua_State *state, int nargs, int nresults);
typedef int (*lua_pcall_ptr)(lua_State *state, int nargs, int nresults, int errfunc);
typedef void (*lua_getfield_ptr)(lua_State *state, int index, const char *k);
typedef void (*lua_setfield_ptr)(lua_State *state, int index, const char *k);
typedef int (*lua_gettop_ptr)(lua_State *state);
typedef void (*lua_settop_ptr)(lua_State *state, int index);
typedef void (*lua_pushvalue_ptr)(lua_State *state, int index);
typedef void (*lua_pushcclosure_ptr)(lua_State *state, lua_CFunction f, int n);
typedef const char *(*lua_tolstring_ptr)(lua_State *state, int index, size_t *len);
typedef int (*lua_type_ptr)(lua_State *state, int index);
typedef void (*lua_pushstring_ptr)(lua_State *state, const char *string);
typedef void (*lua_pushnumber_ptr)(lua_State *state, double number);
typedef void (*lua_pushboolean_ptr)(lua_State *state, int b);
typedef void (*lua_settable_ptr)(lua_State *state, int index);
typedef void (*lua_createtable_ptr)(lua_State *state, int narr, int nrec);
typedef int (*lua_error_ptr)(lua_State *state);
typedef void (*luaL_register_ptr)(lua_State *state, const char *libname,
		const luaL_Reg *l);
typedef const char *(*luaL_checklstring_ptr)(lua_State *state, int index,
		size_t *len);

struct LuaLib {
	lua_call_ptr lua_call;
	lua_pcall_ptr lua_pcall;
	lua_getfield_ptr lua_getfield;
	lua_setfield_ptr lua_setfield;
	lua_gettop_ptr lua_gettop;
	lua_settop_ptr lua_settop;
	lua_pushvalue_ptr lua_pushvalue;
	lua_pushcclosure_ptr lua_pushcclosure;
	lua_tolstring_ptr lua_tolstring;
	lua_type_ptr lua_type;
	lua_pushstring_ptr lua_pushstring;
	lua_pushnumber_ptr lua_pushnumber;
	lua_pushboolean_ptr lua_pushboolean;
	lua_settable_ptr lua_settable;
	lua_createtable_ptr lua_createtable;
	lua_error_ptr lua_error;
	luaL_register_ptr luaL_register;
	luaL_checklstring_ptr luaL_checklstring;
};

void lovely_init(luaL_loadbufferx_ptr, struct LuaLib);

int lovely_loadbufferx(lua_State *L, const char *buf, size_t size,
		const char *name, const char *mode);

static LuaLib get_lua_api_pointers() {
	LuaLib lib;
	lib.lua_call = lua_call;
	lib.lua_pcall = lua_pcall;
	lib.lua_getfield = lua_getfield;
	lib.lua_setfield = lua_setfield;
	lib.lua_gettop = lua_gettop;
	lib.lua_settop = lua_settop;
	lib.lua_pushvalue = lua_pushvalue;
	lib.lua_pushcclosure = lua_pushcclosure;
	lib.lua_tolstring = lua_tolstring;
	lib.lua_type = lua_type;
	lib.lua_pushstring = lua_pushstring;
	lib.lua_pushnumber = lua_pushnumber;
	lib.lua_pushboolean = lua_pushboolean;
	lib.lua_settable = lua_settable;
	lib.lua_createtable = lua_createtable;
	lib.lua_error = lua_error;
	lib.luaL_register = luaL_register;
	lib.luaL_checklstring = luaL_checklstring;
	return lib;
}

JNIEXPORT void JNICALL
Java_org_love2d_android_GameActivity_nativeInitializeLovely(JNIEnv *env, jclass,
		jstring mod_dir) {
	if (mod_dir == nullptr)
		return;

	const char *mod_dir_str = env->GetStringUTFChars(mod_dir, 0);
	if (mod_dir_str == nullptr)
		return;

#if defined(_WIN32)
	_putenv_s("LOVELY_MOD_DIR", mod_dir_str);
#else
	setenv("LOVELY_MOD_DIR", mod_dir_str, 1);
#endif

	LuaLib lualib = get_lua_api_pointers();
	lovely_init(lovely_loadbufferx, lualib);

	env->ReleaseStringUTFChars(mod_dir, mod_dir_str);
}
}
