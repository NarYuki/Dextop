#ifndef HIDL_GENERATED_VENDOR_QTI_HARDWARE_DISPLAY_MAPPEREXTENSIONS_V1_0_TYPES_H
#define HIDL_GENERATED_VENDOR_QTI_HARDWARE_DISPLAY_MAPPEREXTENSIONS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace vendor {
namespace qti {
namespace hardware {
namespace display {
namespace mapperextensions {
namespace V1_0 {

enum class Error : int32_t;
struct YCbCrLayout;
enum class PlaneComponent : uint32_t;
enum class Flags : int32_t;
struct PlaneLayout;

enum class Error : int32_t {
  NONE = 0,
  BAD_BUFFER = 2,
  NO_RESOURCES = 5,
  UNSUPPORTED = 7,
};

struct YCbCrLayout final {
  void* y;
  void* cb;
  void* cr;
  uint32_t yStride;
  uint32_t cStride;
  uint32_t chromaStep;
};

enum class PlaneComponent : uint32_t {
  PLANE_COMPONENT_Y = 1u  ,
  PLANE_COMPONENT_Cb = 2u  ,
  PLANE_COMPONENT_Cr = 4u  ,
  PLANE_COMPONENT_R = 1024u  ,
  PLANE_COMPONENT_G = 2048u  ,
  PLANE_COMPONENT_B = 4096u  ,
  PLANE_COMPONENT_A = 1048576u  ,
  PLANE_COMPONENT_RAW = 1073741824u  ,
  PLANE_COMPONENT_META = 2147483648u  ,
};

enum class Flags : int32_t {
  LAYOUT_INTERLACED_FLAG = 1  ,
};

struct PlaneLayout final {
  ::android::hardware::hidl_bitfield<
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent>
      component __attribute__((aligned(4)));
  uint32_t h_subsampling __attribute__((aligned(4)));
  uint32_t v_subsampling __attribute__((aligned(4)));
  uint32_t offset __attribute__((aligned(4)));
  int32_t pixel_increment __attribute__((aligned(4)));
  int32_t stride __attribute__((aligned(4)));
  int32_t stride_bytes __attribute__((aligned(4)));
  int32_t scanlines __attribute__((aligned(4)));
  uint32_t size __attribute__((aligned(4)));
};

static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       component) == 0,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       h_subsampling) == 4,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       v_subsampling) == 8,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       offset) == 12,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       pixel_increment) == 16,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       stride) == 20,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       stride_bytes) == 24,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       scanlines) == 28,
              "wrong offset");
static_assert(offsetof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout,
                       size) == 32,
              "wrong offset");
static_assert(sizeof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout) == 36,
              "wrong size");
static_assert(__alignof(::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout) == 4,
              "wrong alignment");


template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(
    ::vendor::qti::hardware::display::mapperextensions::V1_0::Error o);

constexpr int32_t operator|(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(
    const int32_t lhs, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error lhs, const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(
    const int32_t lhs, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error lhs, const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(
    int32_t& v, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(
    int32_t& v, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Error e) {
  v &= static_cast<int32_t>(e);
  return v;
}

static inline std::string toString(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::YCbCrLayout& o);

template <typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(
    ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent o);

constexpr uint32_t operator|(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent rhs) {
  return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(
    const uint32_t lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent rhs) {
  return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent lhs,
    const uint32_t rhs) {
  return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent rhs) {
  return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(
    const uint32_t lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent rhs) {
  return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent lhs,
    const uint32_t rhs) {
  return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t& operator|=(
    uint32_t& v, const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent e) {
  v |= static_cast<uint32_t>(e);
  return v;
}
constexpr uint32_t& operator&=(
    uint32_t& v, const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent e) {
  v &= static_cast<uint32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(
    ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags o);

constexpr int32_t operator|(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(
    const int32_t lhs, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags lhs, const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(
    const int32_t lhs, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags lhs, const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(
    int32_t& v, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(
    int32_t& v, const ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags e) {
  v &= static_cast<int32_t>(e);
  return v;
}

static inline std::string toString(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& o);
static inline bool operator==(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& rhs);
static inline bool operator!=(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& rhs);


template <>
inline std::string toString<::vendor::qti::hardware::display::mapperextensions::V1_0::Error>(
    int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<
      ::vendor::qti::hardware::display::mapperextensions::V1_0::Error>
      flipped = 0;
  bool first = true;
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NONE) ==
      static_cast<int32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NONE)) {
    os += (first ? "" : " | ");
    os += "NONE";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NONE;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::BAD_BUFFER) ==
      static_cast<int32_t>(
          ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::BAD_BUFFER)) {
    os += (first ? "" : " | ");
    os += "BAD_BUFFER";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::BAD_BUFFER;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NO_RESOURCES) ==
      static_cast<int32_t>(
          ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NO_RESOURCES)) {
    os += (first ? "" : " | ");
    os += "NO_RESOURCES";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NO_RESOURCES;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::UNSUPPORTED) ==
      static_cast<int32_t>(
          ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::UNSUPPORTED)) {
    os += (first ? "" : " | ");
    os += "UNSUPPORTED";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::UNSUPPORTED;
  }
  if (o != flipped) {
    os += (first ? "" : " | ");
    os += toHexString(o & (~flipped));
  }
  os += " (";
  os += toHexString(o);
  os += ")";
  return os;
}

static inline std::string toString(
    ::vendor::qti::hardware::display::mapperextensions::V1_0::Error o) {
  using ::android::hardware::details::toHexString;
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NONE) {
    return "NONE";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::BAD_BUFFER) {
    return "BAD_BUFFER";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NO_RESOURCES) {
    return "NO_RESOURCES";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::UNSUPPORTED) {
    return "UNSUPPORTED";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

static inline std::string toString(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::YCbCrLayout& o) {
  using ::android::hardware::toString;
  std::string os;
  os += "{";
  os += ".y = ";
  os += ::android::hardware::toString(o.y);
  os += ", .cb = ";
  os += ::android::hardware::toString(o.cb);
  os += ", .cr = ";
  os += ::android::hardware::toString(o.cr);
  os += ", .yStride = ";
  os += ::android::hardware::toString(o.yStride);
  os += ", .cStride = ";
  os += ::android::hardware::toString(o.cStride);
  os += ", .chromaStep = ";
  os += ::android::hardware::toString(o.chromaStep);
  os += "}";
  return os;
}


template <>
inline std::string
toString<::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent>(uint32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent>
      flipped = 0;
  bool first = true;
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_Y) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_Y)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_Y";
    first = false;
    flipped |=
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_Y;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_Cb) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_Cb)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_Cb";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
        PLANE_COMPONENT_Cb;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_Cr) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_Cr)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_Cr";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
        PLANE_COMPONENT_Cr;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_R) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_R)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_R";
    first = false;
    flipped |=
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_R;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_G) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_G)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_G";
    first = false;
    flipped |=
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_G;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_B) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_B)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_B";
    first = false;
    flipped |=
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_B;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_A) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_A)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_A";
    first = false;
    flipped |=
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_A;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_RAW) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_RAW)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_RAW";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
        PLANE_COMPONENT_RAW;
  }
  if ((o & ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_META) ==
      static_cast<uint32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::
                                PlaneComponent::PLANE_COMPONENT_META)) {
    os += (first ? "" : " | ");
    os += "PLANE_COMPONENT_META";
    first = false;
    flipped |= ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
        PLANE_COMPONENT_META;
  }
  if (o != flipped) {
    os += (first ? "" : " | ");
    os += toHexString(o & (~flipped));
  }
  os += " (";
  os += toHexString(o);
  os += ")";
  return os;
}

static inline std::string toString(
    ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent o) {
  using ::android::hardware::details::toHexString;
  if (o ==
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_Y) {
    return "PLANE_COMPONENT_Y";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_Cb) {
    return "PLANE_COMPONENT_Cb";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_Cr) {
    return "PLANE_COMPONENT_Cr";
  }
  if (o ==
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_R) {
    return "PLANE_COMPONENT_R";
  }
  if (o ==
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_G) {
    return "PLANE_COMPONENT_G";
  }
  if (o ==
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_B) {
    return "PLANE_COMPONENT_B";
  }
  if (o ==
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_A) {
    return "PLANE_COMPONENT_A";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_RAW) {
    return "PLANE_COMPONENT_RAW";
  }
  if (o == ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
               PLANE_COMPONENT_META) {
    return "PLANE_COMPONENT_META";
  }
  std::string os;
  os += toHexString(static_cast<uint32_t>(o));
  return os;
}

template <>
inline std::string toString<::vendor::qti::hardware::display::mapperextensions::V1_0::Flags>(
    int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<
      ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags>
      flipped = 0;
  bool first = true;
  if ((o &
       ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags::LAYOUT_INTERLACED_FLAG) ==
      static_cast<int32_t>(::vendor::qti::hardware::display::mapperextensions::V1_0::Flags::
                               LAYOUT_INTERLACED_FLAG)) {
    os += (first ? "" : " | ");
    os += "LAYOUT_INTERLACED_FLAG";
    first = false;
    flipped |=
        ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags::LAYOUT_INTERLACED_FLAG;
  }
  if (o != flipped) {
    os += (first ? "" : " | ");
    os += toHexString(o & (~flipped));
  }
  os += " (";
  os += toHexString(o);
  os += ")";
  return os;
}

static inline std::string toString(
    ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags o) {
  using ::android::hardware::details::toHexString;
  if (o ==
      ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags::LAYOUT_INTERLACED_FLAG) {
    return "LAYOUT_INTERLACED_FLAG";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

static inline std::string toString(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& o) {
  using ::android::hardware::toString;
  std::string os;
  os += "{";
  os += ".component = ";
  os += ::vendor::qti::hardware::display::mapperextensions::V1_0::toString<
      ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent>(o.component);
  os += ", .h_subsampling = ";
  os += ::android::hardware::toString(o.h_subsampling);
  os += ", .v_subsampling = ";
  os += ::android::hardware::toString(o.v_subsampling);
  os += ", .offset = ";
  os += ::android::hardware::toString(o.offset);
  os += ", .pixel_increment = ";
  os += ::android::hardware::toString(o.pixel_increment);
  os += ", .stride = ";
  os += ::android::hardware::toString(o.stride);
  os += ", .stride_bytes = ";
  os += ::android::hardware::toString(o.stride_bytes);
  os += ", .scanlines = ";
  os += ::android::hardware::toString(o.scanlines);
  os += ", .size = ";
  os += ::android::hardware::toString(o.size);
  os += "}";
  return os;
}

static inline bool operator==(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& rhs) {
  if (lhs.component != rhs.component) {
    return false;
  }
  if (lhs.h_subsampling != rhs.h_subsampling) {
    return false;
  }
  if (lhs.v_subsampling != rhs.v_subsampling) {
    return false;
  }
  if (lhs.offset != rhs.offset) {
    return false;
  }
  if (lhs.pixel_increment != rhs.pixel_increment) {
    return false;
  }
  if (lhs.stride != rhs.stride) {
    return false;
  }
  if (lhs.stride_bytes != rhs.stride_bytes) {
    return false;
  }
  if (lhs.scanlines != rhs.scanlines) {
    return false;
  }
  if (lhs.size != rhs.size) {
    return false;
  }
  return true;
}

static inline bool operator!=(
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& lhs,
    const ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneLayout& rhs) {
  return !(lhs == rhs);
}

}
}
}
}
}
}


namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::vendor::qti::hardware::display::mapperextensions::V1_0::Error, 4>
    hidl_enum_values<::vendor::qti::hardware::display::mapperextensions::V1_0::Error> = {
        ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NONE,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::BAD_BUFFER,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::NO_RESOURCES,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::Error::UNSUPPORTED,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent, 9>
    hidl_enum_values<::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent> = {
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_Y,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
            PLANE_COMPONENT_Cb,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
            PLANE_COMPONENT_Cr,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_R,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_G,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_B,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::PLANE_COMPONENT_A,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
            PLANE_COMPONENT_RAW,
        ::vendor::qti::hardware::display::mapperextensions::V1_0::PlaneComponent::
            PLANE_COMPONENT_META,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::vendor::qti::hardware::display::mapperextensions::V1_0::Flags, 1>
    hidl_enum_values<::vendor::qti::hardware::display::mapperextensions::V1_0::Flags> = {
        ::vendor::qti::hardware::display::mapperextensions::V1_0::Flags::LAYOUT_INTERLACED_FLAG,
};
}
}
}

#endif
