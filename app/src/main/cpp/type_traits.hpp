#pragma once

#include <type_traits>

namespace lsplant {
template <class, template <class, class...> class>
struct is_instance : public std::false_type {};

template <class... Ts, template <class, class...> class U>
struct is_instance<U<Ts...>, U> : public std::true_type {};

template <class T, template <class, class...> class U>
inline constexpr bool is_instance_v = is_instance<T, U>::value;
}  // namespace lsplant
