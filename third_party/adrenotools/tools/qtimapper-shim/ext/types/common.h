#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_COMMON_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_COMMON_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace graphics {
namespace common {
namespace V1_0 {

enum class PixelFormat : int32_t;
enum class BufferUsage : uint64_t;
enum class Transform : int32_t;
enum class Dataspace : int32_t;
enum class ColorMode : int32_t;
enum class ColorTransform : int32_t;
enum class Hdr : int32_t;

enum class PixelFormat : int32_t {







  RGBA_8888 = 1  ,







  RGBX_8888 = 2  ,







  RGB_888 = 3  ,








  RGB_565 = 4  ,







  BGRA_8888 = 5  ,



  YCBCR_422_SP = 16  ,
  YCRCB_420_SP = 17  ,
  YCBCR_422_I = 20  ,







  RGBA_FP16 = 22  ,









































  RAW16 = 32  ,





















  BLOB = 33  ,














  IMPLEMENTATION_DEFINED = 34  ,















  YCBCR_420_888 = 35  ,




















  RAW_OPAQUE = 36  ,





















































  RAW10 = 37  ,

















































  RAW12 = 38  ,











  RGBA_1010102 = 43  ,





























  Y8 = 538982489  ,

























  Y16 = 540422489  ,































  YV12 = 842094169  ,
};

enum class BufferUsage : uint64_t {



  CPU_READ_MASK = 15ull  ,



  CPU_READ_NEVER = 0ull,
  CPU_READ_RARELY = 2ull,
  CPU_READ_OFTEN = 3ull,



  CPU_WRITE_MASK = 240ull  ,



  CPU_WRITE_NEVER = 0ull  ,



  CPU_WRITE_RARELY = 32ull  ,



  CPU_WRITE_OFTEN = 48ull  ,



  GPU_TEXTURE = 256ull  ,



  GPU_RENDER_TARGET = 512ull  ,






  COMPOSER_OVERLAY = 2048ull  ,



  COMPOSER_CLIENT_TARGET = 4096ull  ,








  PROTECTED = 16384ull  ,



  COMPOSER_CURSOR = 32768ull  ,



  VIDEO_ENCODER = 65536ull  ,



  CAMERA_OUTPUT = 131072ull  ,



  CAMERA_INPUT = 262144ull  ,






  RENDERSCRIPT = 1048576ull  ,






  VIDEO_DECODER = 4194304ull  ,



  SENSOR_DIRECT_DATA = 8388608ull  ,




  GPU_DATA_BUFFER = 16777216ull  ,






  VENDOR_MASK = 4026531840ull  ,






  VENDOR_MASK_HI = 18446462598732840960ull  ,
};

enum class Transform : int32_t {



  FLIP_H = 1  ,



  FLIP_V = 2  ,



  ROT_90 = 4  ,



  ROT_180 = 3  ,
  ROT_270 = 7  ,
};

enum class Dataspace : int32_t {









  UNKNOWN = 0  ,











  ARBITRARY = 1  ,







  STANDARD_SHIFT = 16,






  STANDARD_MASK = 4128768  ,













  STANDARD_UNSPECIFIED = 0  ,










  STANDARD_BT709 = 65536  ,












  STANDARD_BT601_625 = 131072  ,










  STANDARD_BT601_625_UNADJUSTED = 196608  ,












  STANDARD_BT601_525 = 262144  ,










  STANDARD_BT601_525_UNADJUSTED = 327680  ,










  STANDARD_BT2020 = 393216  ,










  STANDARD_BT2020_CONSTANT_LUMINANCE = 458752  ,










  STANDARD_BT470M = 524288  ,










  STANDARD_FILM = 589824  ,








  STANDARD_DCI_P3 = 655360  ,








  STANDARD_ADOBE_RGB = 720896  ,
  TRANSFER_SHIFT = 22,













  TRANSFER_MASK = 130023424  ,












  TRANSFER_UNSPECIFIED = 0  ,






  TRANSFER_LINEAR = 4194304  ,








  TRANSFER_SRGB = 8388608  ,









  TRANSFER_SMPTE_170M = 12582912  ,








  TRANSFER_GAMMA2_2 = 16777216  ,








  TRANSFER_GAMMA2_6 = 20971520  ,








  TRANSFER_GAMMA2_8 = 25165824  ,














  TRANSFER_ST2084 = 29360128  ,














  TRANSFER_HLG = 33554432  ,
  RANGE_SHIFT = 27,






  RANGE_MASK = 939524096  ,











  RANGE_UNSPECIFIED = 0  ,




  RANGE_FULL = 134217728  ,













  RANGE_LIMITED = 268435456  ,







  RANGE_EXTENDED = 402653184  ,













  SRGB_LINEAR = 512  ,
  V0_SRGB_LINEAR = 138477568  ,












  V0_SCRGB_LINEAR = 406913024  ,













  SRGB = 513  ,
  V0_SRGB = 142671872  ,












  V0_SCRGB = 411107328  ,

















  JFIF = 257  ,
  V0_JFIF = 146931712  ,







  BT601_625 = 258  ,
  V0_BT601_625 = 281149440  ,







  BT601_525 = 259  ,
  V0_BT601_525 = 281280512  ,







  BT709 = 260  ,
  V0_BT709 = 281083904  ,







  DCI_P3_LINEAR = 139067392  ,









  DCI_P3 = 155844608  ,






  DISPLAY_P3_LINEAR = 139067392  ,






  DISPLAY_P3 = 143261696  ,







  ADOBE_RGB = 151715840  ,







  BT2020_LINEAR = 138805248  ,







  BT2020 = 147193856  ,







  BT2020_PQ = 163971072  ,





















  DEPTH = 4096  ,







  SENSOR = 4097  ,
};

enum class ColorMode : int32_t {
  NATIVE = 0,
  STANDARD_BT601_625 = 1,
  STANDARD_BT601_625_UNADJUSTED = 2,
  STANDARD_BT601_525 = 3,
  STANDARD_BT601_525_UNADJUSTED = 4,
  STANDARD_BT709 = 5,
  DCI_P3 = 6,
  SRGB = 7,
  ADOBE_RGB = 8,
  DISPLAY_P3 = 9,
};

enum class ColorTransform : int32_t {
  IDENTITY = 0,
  ARBITRARY_MATRIX = 1,
  VALUE_INVERSE = 2,
  GRAYSCALE = 3,
  CORRECT_PROTANOPIA = 4,
  CORRECT_DEUTERANOPIA = 5,
  CORRECT_TRITANOPIA = 6,
};

enum class Hdr : int32_t {
  DOLBY_VISION = 1,
  HDR10 = 2,
  HLG = 3,
};


template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::PixelFormat o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::PixelFormat lhs,
                            const ::android::hardware::graphics::common::V1_0::PixelFormat rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::PixelFormat rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::PixelFormat lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::PixelFormat lhs,
                            const ::android::hardware::graphics::common::V1_0::PixelFormat rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::PixelFormat rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::PixelFormat lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::PixelFormat e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::PixelFormat e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::BufferUsage o);

constexpr uint64_t operator|(const ::android::hardware::graphics::common::V1_0::BufferUsage lhs,
                             const ::android::hardware::graphics::common::V1_0::BufferUsage rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs,
                             const ::android::hardware::graphics::common::V1_0::BufferUsage rhs) {
  return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::graphics::common::V1_0::BufferUsage lhs,
                             const uint64_t rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::graphics::common::V1_0::BufferUsage lhs,
                             const ::android::hardware::graphics::common::V1_0::BufferUsage rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs,
                             const ::android::hardware::graphics::common::V1_0::BufferUsage rhs) {
  return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::graphics::common::V1_0::BufferUsage lhs,
                             const uint64_t rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t& operator|=(uint64_t& v,
                               const ::android::hardware::graphics::common::V1_0::BufferUsage e) {
  v |= static_cast<uint64_t>(e);
  return v;
}
constexpr uint64_t& operator&=(uint64_t& v,
                               const ::android::hardware::graphics::common::V1_0::BufferUsage e) {
  v &= static_cast<uint64_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::Transform o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::Transform lhs,
                            const ::android::hardware::graphics::common::V1_0::Transform rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::Transform rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::Transform lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::Transform lhs,
                            const ::android::hardware::graphics::common::V1_0::Transform rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::Transform rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::Transform lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::Transform e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::Transform e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::Dataspace o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::Dataspace lhs,
                            const ::android::hardware::graphics::common::V1_0::Dataspace rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::Dataspace rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::Dataspace lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::Dataspace lhs,
                            const ::android::hardware::graphics::common::V1_0::Dataspace rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::Dataspace rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::Dataspace lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::Dataspace e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::Dataspace e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::ColorMode o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::ColorMode lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorMode rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorMode rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::ColorMode lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::ColorMode lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorMode rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorMode rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::ColorMode lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::ColorMode e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::ColorMode e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::ColorTransform o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::ColorTransform lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorTransform rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorTransform rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::ColorTransform lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::ColorTransform lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorTransform rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::ColorTransform rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::ColorTransform lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::ColorTransform e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::ColorTransform e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_0::Hdr o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::Hdr lhs,
                            const ::android::hardware::graphics::common::V1_0::Hdr rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::Hdr rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_0::Hdr lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::Hdr lhs,
                            const ::android::hardware::graphics::common::V1_0::Hdr rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_0::Hdr rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_0::Hdr lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::Hdr e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_0::Hdr e) {
  v &= static_cast<int32_t>(e);
  return v;
}


template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::PixelFormat>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::PixelFormat>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_8888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_8888)) {
    os += (first ? "" : " | ");
    os += "RGBA_8888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_8888;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RGBX_8888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RGBX_8888)) {
    os += (first ? "" : " | ");
    os += "RGBX_8888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RGBX_8888;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RGB_888)) {
    os += (first ? "" : " | ");
    os += "RGB_888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_888;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_565) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RGB_565)) {
    os += (first ? "" : " | ");
    os += "RGB_565";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_565;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::BGRA_8888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::BGRA_8888)) {
    os += (first ? "" : " | ");
    os += "BGRA_8888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::BGRA_8888;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_SP) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_SP)) {
    os += (first ? "" : " | ");
    os += "YCBCR_422_SP";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_SP;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::YCRCB_420_SP) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::PixelFormat::YCRCB_420_SP)) {
    os += (first ? "" : " | ");
    os += "YCRCB_420_SP";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::YCRCB_420_SP;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_I) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_I)) {
    os += (first ? "" : " | ");
    os += "YCBCR_422_I";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_I;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_FP16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_FP16)) {
    os += (first ? "" : " | ");
    os += "RGBA_FP16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_FP16;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RAW16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RAW16)) {
    os += (first ? "" : " | ");
    os += "RAW16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RAW16;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::BLOB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::BLOB)) {
    os += (first ? "" : " | ");
    os += "BLOB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::BLOB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::IMPLEMENTATION_DEFINED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::PixelFormat::IMPLEMENTATION_DEFINED)) {
    os += (first ? "" : " | ");
    os += "IMPLEMENTATION_DEFINED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::IMPLEMENTATION_DEFINED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_420_888) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_420_888)) {
    os += (first ? "" : " | ");
    os += "YCBCR_420_888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_420_888;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RAW_OPAQUE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RAW_OPAQUE)) {
    os += (first ? "" : " | ");
    os += "RAW_OPAQUE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RAW_OPAQUE;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RAW10) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RAW10)) {
    os += (first ? "" : " | ");
    os += "RAW10";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RAW10;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RAW12) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::RAW12)) {
    os += (first ? "" : " | ");
    os += "RAW12";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RAW12;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_1010102) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_1010102)) {
    os += (first ? "" : " | ");
    os += "RGBA_1010102";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_1010102;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::Y8) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::Y8)) {
    os += (first ? "" : " | ");
    os += "Y8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::Y8;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::Y16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::Y16)) {
    os += (first ? "" : " | ");
    os += "Y16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::Y16;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::PixelFormat::YV12) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::PixelFormat::YV12)) {
    os += (first ? "" : " | ");
    os += "YV12";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::PixelFormat::YV12;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::PixelFormat o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_8888) {
    return "RGBA_8888";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RGBX_8888) {
    return "RGBX_8888";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_888) {
    return "RGB_888";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_565) {
    return "RGB_565";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::BGRA_8888) {
    return "BGRA_8888";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_SP) {
    return "YCBCR_422_SP";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::YCRCB_420_SP) {
    return "YCRCB_420_SP";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_I) {
    return "YCBCR_422_I";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_FP16) {
    return "RGBA_FP16";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RAW16) {
    return "RAW16";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::BLOB) {
    return "BLOB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::IMPLEMENTATION_DEFINED) {
    return "IMPLEMENTATION_DEFINED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_420_888) {
    return "YCBCR_420_888";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RAW_OPAQUE) {
    return "RAW_OPAQUE";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RAW10) {
    return "RAW10";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RAW12) {
    return "RAW12";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_1010102) {
    return "RGBA_1010102";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::Y8) {
    return "Y8";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::Y16) {
    return "Y16";
  }
  if (o == ::android::hardware::graphics::common::V1_0::PixelFormat::YV12) {
    return "YV12";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::BufferUsage>(uint64_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::BufferUsage>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_MASK) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_MASK)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_NEVER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_NEVER)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_NEVER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_NEVER;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_RARELY) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_RARELY)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_RARELY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_RARELY;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_OFTEN) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_OFTEN)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_OFTEN";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_OFTEN;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_MASK) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_MASK)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_NEVER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_NEVER)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_NEVER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_NEVER;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_RARELY) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_RARELY)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_RARELY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_RARELY;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_OFTEN) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_OFTEN)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_OFTEN";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_OFTEN;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_TEXTURE) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_TEXTURE)) {
    os += (first ? "" : " | ");
    os += "GPU_TEXTURE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_TEXTURE;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_RENDER_TARGET) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_RENDER_TARGET)) {
    os += (first ? "" : " | ");
    os += "GPU_RENDER_TARGET";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_RENDER_TARGET;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_OVERLAY) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_OVERLAY)) {
    os += (first ? "" : " | ");
    os += "COMPOSER_OVERLAY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_OVERLAY;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CLIENT_TARGET) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CLIENT_TARGET)) {
    os += (first ? "" : " | ");
    os += "COMPOSER_CLIENT_TARGET";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CLIENT_TARGET;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::PROTECTED) ==
      static_cast<uint64_t>(::android::hardware::graphics::common::V1_0::BufferUsage::PROTECTED)) {
    os += (first ? "" : " | ");
    os += "PROTECTED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::PROTECTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CURSOR) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CURSOR)) {
    os += (first ? "" : " | ");
    os += "COMPOSER_CURSOR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CURSOR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_ENCODER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_ENCODER)) {
    os += (first ? "" : " | ");
    os += "VIDEO_ENCODER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_ENCODER;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_OUTPUT) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_OUTPUT)) {
    os += (first ? "" : " | ");
    os += "CAMERA_OUTPUT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_OUTPUT;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_INPUT) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_INPUT)) {
    os += (first ? "" : " | ");
    os += "CAMERA_INPUT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_INPUT;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::RENDERSCRIPT) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::RENDERSCRIPT)) {
    os += (first ? "" : " | ");
    os += "RENDERSCRIPT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::RENDERSCRIPT;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_DECODER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_DECODER)) {
    os += (first ? "" : " | ");
    os += "VIDEO_DECODER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_DECODER;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::SENSOR_DIRECT_DATA) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::SENSOR_DIRECT_DATA)) {
    os += (first ? "" : " | ");
    os += "SENSOR_DIRECT_DATA";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::SENSOR_DIRECT_DATA;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_DATA_BUFFER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_DATA_BUFFER)) {
    os += (first ? "" : " | ");
    os += "GPU_DATA_BUFFER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_DATA_BUFFER;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK)) {
    os += (first ? "" : " | ");
    os += "VENDOR_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK_HI) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK_HI)) {
    os += (first ? "" : " | ");
    os += "VENDOR_MASK_HI";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK_HI;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::BufferUsage o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_MASK) {
    return "CPU_READ_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_NEVER) {
    return "CPU_READ_NEVER";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_RARELY) {
    return "CPU_READ_RARELY";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_OFTEN) {
    return "CPU_READ_OFTEN";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_MASK) {
    return "CPU_WRITE_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_NEVER) {
    return "CPU_WRITE_NEVER";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_RARELY) {
    return "CPU_WRITE_RARELY";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_OFTEN) {
    return "CPU_WRITE_OFTEN";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_TEXTURE) {
    return "GPU_TEXTURE";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_RENDER_TARGET) {
    return "GPU_RENDER_TARGET";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_OVERLAY) {
    return "COMPOSER_OVERLAY";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CLIENT_TARGET) {
    return "COMPOSER_CLIENT_TARGET";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::PROTECTED) {
    return "PROTECTED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CURSOR) {
    return "COMPOSER_CURSOR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_ENCODER) {
    return "VIDEO_ENCODER";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_OUTPUT) {
    return "CAMERA_OUTPUT";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_INPUT) {
    return "CAMERA_INPUT";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::RENDERSCRIPT) {
    return "RENDERSCRIPT";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_DECODER) {
    return "VIDEO_DECODER";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::SENSOR_DIRECT_DATA) {
    return "SENSOR_DIRECT_DATA";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_DATA_BUFFER) {
    return "GPU_DATA_BUFFER";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK) {
    return "VENDOR_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK_HI) {
    return "VENDOR_MASK_HI";
  }
  std::string os;
  os += toHexString(static_cast<uint64_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::Transform>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::Transform>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::Transform::FLIP_H) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Transform::FLIP_H)) {
    os += (first ? "" : " | ");
    os += "FLIP_H";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Transform::FLIP_H;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Transform::FLIP_V) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Transform::FLIP_V)) {
    os += (first ? "" : " | ");
    os += "FLIP_V";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Transform::FLIP_V;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Transform::ROT_90) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Transform::ROT_90)) {
    os += (first ? "" : " | ");
    os += "ROT_90";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Transform::ROT_90;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Transform::ROT_180) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Transform::ROT_180)) {
    os += (first ? "" : " | ");
    os += "ROT_180";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Transform::ROT_180;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Transform::ROT_270) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Transform::ROT_270)) {
    os += (first ? "" : " | ");
    os += "ROT_270";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Transform::ROT_270;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::Transform o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::Transform::FLIP_H) {
    return "FLIP_H";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Transform::FLIP_V) {
    return "FLIP_V";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Transform::ROT_90) {
    return "ROT_90";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Transform::ROT_180) {
    return "ROT_180";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Transform::ROT_270) {
    return "ROT_270";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::Dataspace>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::Dataspace>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN)) {
    os += (first ? "" : " | ");
    os += "UNKNOWN";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::ARBITRARY) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::ARBITRARY)) {
    os += (first ? "" : " | ");
    os += "ARBITRARY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::ARBITRARY;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_SHIFT) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_SHIFT)) {
    os += (first ? "" : " | ");
    os += "STANDARD_SHIFT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_SHIFT;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_MASK) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_MASK)) {
    os += (first ? "" : " | ");
    os += "STANDARD_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_UNSPECIFIED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_UNSPECIFIED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_UNSPECIFIED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_UNSPECIFIED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT709) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT709)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT2020";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::
               STANDARD_BT2020_CONSTANT_LUMINANCE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::
                               STANDARD_BT2020_CONSTANT_LUMINANCE)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT2020_CONSTANT_LUMINANCE";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020_CONSTANT_LUMINANCE;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT470M) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT470M)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT470M";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT470M;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_FILM) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_FILM)) {
    os += (first ? "" : " | ");
    os += "STANDARD_FILM";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_FILM;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_DCI_P3) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_DCI_P3)) {
    os += (first ? "" : " | ");
    os += "STANDARD_DCI_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_DCI_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_ADOBE_RGB) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_ADOBE_RGB)) {
    os += (first ? "" : " | ");
    os += "STANDARD_ADOBE_RGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_ADOBE_RGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SHIFT) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SHIFT)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_SHIFT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SHIFT;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_MASK) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_MASK)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_UNSPECIFIED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_UNSPECIFIED)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_UNSPECIFIED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_UNSPECIFIED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_LINEAR)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SRGB)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SMPTE_170M) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SMPTE_170M)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_SMPTE_170M";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SMPTE_170M;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_2) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_2)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_GAMMA2_2";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_2;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_6) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_6)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_GAMMA2_6";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_6;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_8) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_8)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_GAMMA2_8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_8;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_ST2084) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_ST2084)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_ST2084";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_ST2084;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_HLG) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_HLG)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_HLG";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_HLG;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_SHIFT) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::RANGE_SHIFT)) {
    os += (first ? "" : " | ");
    os += "RANGE_SHIFT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_SHIFT;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_MASK) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::RANGE_MASK)) {
    os += (first ? "" : " | ");
    os += "RANGE_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_UNSPECIFIED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_UNSPECIFIED)) {
    os += (first ? "" : " | ");
    os += "RANGE_UNSPECIFIED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_UNSPECIFIED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_FULL) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::RANGE_FULL)) {
    os += (first ? "" : " | ");
    os += "RANGE_FULL";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_FULL;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_LIMITED) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::RANGE_LIMITED)) {
    os += (first ? "" : " | ");
    os += "RANGE_LIMITED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_LIMITED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_EXTENDED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_EXTENDED)) {
    os += (first ? "" : " | ");
    os += "RANGE_EXTENDED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_EXTENDED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::SRGB_LINEAR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::SRGB_LINEAR)) {
    os += (first ? "" : " | ");
    os += "SRGB_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::SRGB_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB_LINEAR)) {
    os += (first ? "" : " | ");
    os += "V0_SRGB_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB_LINEAR)) {
    os += (first ? "" : " | ");
    os += "V0_SCRGB_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::SRGB)) {
    os += (first ? "" : " | ");
    os += "SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB)) {
    os += (first ? "" : " | ");
    os += "V0_SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB)) {
    os += (first ? "" : " | ");
    os += "V0_SCRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::JFIF) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::JFIF)) {
    os += (first ? "" : " | ");
    os += "JFIF";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::JFIF;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_JFIF) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::V0_JFIF)) {
    os += (first ? "" : " | ");
    os += "V0_JFIF";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_JFIF;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::BT601_625) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::BT601_625)) {
    os += (first ? "" : " | ");
    os += "BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_625) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_625)) {
    os += (first ? "" : " | ");
    os += "V0_BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::BT601_525) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::BT601_525)) {
    os += (first ? "" : " | ");
    os += "BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_525) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_525)) {
    os += (first ? "" : " | ");
    os += "V0_BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::BT709) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::BT709)) {
    os += (first ? "" : " | ");
    os += "BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT709) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::V0_BT709)) {
    os += (first ? "" : " | ");
    os += "V0_BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3_LINEAR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3_LINEAR)) {
    os += (first ? "" : " | ");
    os += "DCI_P3_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3)) {
    os += (first ? "" : " | ");
    os += "DCI_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3_LINEAR)) {
    os += (first ? "" : " | ");
    os += "DISPLAY_P3_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3)) {
    os += (first ? "" : " | ");
    os += "DISPLAY_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::ADOBE_RGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::ADOBE_RGB)) {
    os += (first ? "" : " | ");
    os += "ADOBE_RGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::ADOBE_RGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_LINEAR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::BT2020_LINEAR)) {
    os += (first ? "" : " | ");
    os += "BT2020_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::BT2020) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::BT2020)) {
    os += (first ? "" : " | ");
    os += "BT2020";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::BT2020;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_PQ) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::BT2020_PQ)) {
    os += (first ? "" : " | ");
    os += "BT2020_PQ";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_PQ;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::DEPTH) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::DEPTH)) {
    os += (first ? "" : " | ");
    os += "DEPTH";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::DEPTH;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Dataspace::SENSOR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Dataspace::SENSOR)) {
    os += (first ? "" : " | ");
    os += "SENSOR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Dataspace::SENSOR;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::Dataspace o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN) {
    return "UNKNOWN";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::ARBITRARY) {
    return "ARBITRARY";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_SHIFT) {
    return "STANDARD_SHIFT";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_MASK) {
    return "STANDARD_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_UNSPECIFIED) {
    return "STANDARD_UNSPECIFIED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT709) {
    return "STANDARD_BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625) {
    return "STANDARD_BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625_UNADJUSTED) {
    return "STANDARD_BT601_625_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525) {
    return "STANDARD_BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525_UNADJUSTED) {
    return "STANDARD_BT601_525_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020) {
    return "STANDARD_BT2020";
  }
  if (o ==
      ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020_CONSTANT_LUMINANCE) {
    return "STANDARD_BT2020_CONSTANT_LUMINANCE";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT470M) {
    return "STANDARD_BT470M";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_FILM) {
    return "STANDARD_FILM";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_DCI_P3) {
    return "STANDARD_DCI_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_ADOBE_RGB) {
    return "STANDARD_ADOBE_RGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SHIFT) {
    return "TRANSFER_SHIFT";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_MASK) {
    return "TRANSFER_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_UNSPECIFIED) {
    return "TRANSFER_UNSPECIFIED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_LINEAR) {
    return "TRANSFER_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SRGB) {
    return "TRANSFER_SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SMPTE_170M) {
    return "TRANSFER_SMPTE_170M";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_2) {
    return "TRANSFER_GAMMA2_2";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_6) {
    return "TRANSFER_GAMMA2_6";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_8) {
    return "TRANSFER_GAMMA2_8";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_ST2084) {
    return "TRANSFER_ST2084";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_HLG) {
    return "TRANSFER_HLG";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_SHIFT) {
    return "RANGE_SHIFT";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_MASK) {
    return "RANGE_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_UNSPECIFIED) {
    return "RANGE_UNSPECIFIED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_FULL) {
    return "RANGE_FULL";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_LIMITED) {
    return "RANGE_LIMITED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_EXTENDED) {
    return "RANGE_EXTENDED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::SRGB_LINEAR) {
    return "SRGB_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB_LINEAR) {
    return "V0_SRGB_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB_LINEAR) {
    return "V0_SCRGB_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::SRGB) {
    return "SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB) {
    return "V0_SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB) {
    return "V0_SCRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::JFIF) {
    return "JFIF";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_JFIF) {
    return "V0_JFIF";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::BT601_625) {
    return "BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_625) {
    return "V0_BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::BT601_525) {
    return "BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_525) {
    return "V0_BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::BT709) {
    return "BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT709) {
    return "V0_BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3_LINEAR) {
    return "DCI_P3_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3) {
    return "DCI_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3_LINEAR) {
    return "DISPLAY_P3_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3) {
    return "DISPLAY_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::ADOBE_RGB) {
    return "ADOBE_RGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_LINEAR) {
    return "BT2020_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::BT2020) {
    return "BT2020";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_PQ) {
    return "BT2020_PQ";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::DEPTH) {
    return "DEPTH";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Dataspace::SENSOR) {
    return "SENSOR";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::ColorMode>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::ColorMode>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::NATIVE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::ColorMode::NATIVE)) {
    os += (first ? "" : " | ");
    os += "NATIVE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::NATIVE;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT709) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT709)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::DCI_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::ColorMode::DCI_P3)) {
    os += (first ? "" : " | ");
    os += "DCI_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::DCI_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::ColorMode::SRGB)) {
    os += (first ? "" : " | ");
    os += "SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::ADOBE_RGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::ColorMode::ADOBE_RGB)) {
    os += (first ? "" : " | ");
    os += "ADOBE_RGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::ADOBE_RGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorMode::DISPLAY_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::ColorMode::DISPLAY_P3)) {
    os += (first ? "" : " | ");
    os += "DISPLAY_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorMode::DISPLAY_P3;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::ColorMode o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::NATIVE) {
    return "NATIVE";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625) {
    return "STANDARD_BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625_UNADJUSTED) {
    return "STANDARD_BT601_625_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525) {
    return "STANDARD_BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525_UNADJUSTED) {
    return "STANDARD_BT601_525_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT709) {
    return "STANDARD_BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::DCI_P3) {
    return "DCI_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::SRGB) {
    return "SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::ADOBE_RGB) {
    return "ADOBE_RGB";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorMode::DISPLAY_P3) {
    return "DISPLAY_P3";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::ColorTransform>(
    int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::ColorTransform>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::IDENTITY) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::ColorTransform::IDENTITY)) {
    os += (first ? "" : " | ");
    os += "IDENTITY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::IDENTITY;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::ARBITRARY_MATRIX) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorTransform::ARBITRARY_MATRIX)) {
    os += (first ? "" : " | ");
    os += "ARBITRARY_MATRIX";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::ARBITRARY_MATRIX;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::VALUE_INVERSE) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorTransform::VALUE_INVERSE)) {
    os += (first ? "" : " | ");
    os += "VALUE_INVERSE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::VALUE_INVERSE;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::GRAYSCALE) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorTransform::GRAYSCALE)) {
    os += (first ? "" : " | ");
    os += "GRAYSCALE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::GRAYSCALE;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_PROTANOPIA) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_PROTANOPIA)) {
    os += (first ? "" : " | ");
    os += "CORRECT_PROTANOPIA";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_PROTANOPIA;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_DEUTERANOPIA) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_DEUTERANOPIA)) {
    os += (first ? "" : " | ");
    os += "CORRECT_DEUTERANOPIA";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_DEUTERANOPIA;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_TRITANOPIA) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_TRITANOPIA)) {
    os += (first ? "" : " | ");
    os += "CORRECT_TRITANOPIA";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_TRITANOPIA;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::ColorTransform o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::IDENTITY) {
    return "IDENTITY";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::ARBITRARY_MATRIX) {
    return "ARBITRARY_MATRIX";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::VALUE_INVERSE) {
    return "VALUE_INVERSE";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::GRAYSCALE) {
    return "GRAYSCALE";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_PROTANOPIA) {
    return "CORRECT_PROTANOPIA";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_DEUTERANOPIA) {
    return "CORRECT_DEUTERANOPIA";
  }
  if (o == ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_TRITANOPIA) {
    return "CORRECT_TRITANOPIA";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_0::Hdr>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::Hdr> flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_0::Hdr::DOLBY_VISION) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Hdr::DOLBY_VISION)) {
    os += (first ? "" : " | ");
    os += "DOLBY_VISION";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Hdr::DOLBY_VISION;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Hdr::HDR10) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Hdr::HDR10)) {
    os += (first ? "" : " | ");
    os += "HDR10";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Hdr::HDR10;
  }
  if ((o & ::android::hardware::graphics::common::V1_0::Hdr::HLG) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_0::Hdr::HLG)) {
    os += (first ? "" : " | ");
    os += "HLG";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_0::Hdr::HLG;
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

static inline std::string toString(::android::hardware::graphics::common::V1_0::Hdr o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_0::Hdr::DOLBY_VISION) {
    return "DOLBY_VISION";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Hdr::HDR10) {
    return "HDR10";
  }
  if (o == ::android::hardware::graphics::common::V1_0::Hdr::HLG) {
    return "HLG";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
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
constexpr std::array<::android::hardware::graphics::common::V1_0::PixelFormat, 20>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::PixelFormat> = {
        ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_8888,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RGBX_8888,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_888,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RGB_565,
        ::android::hardware::graphics::common::V1_0::PixelFormat::BGRA_8888,
        ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_SP,
        ::android::hardware::graphics::common::V1_0::PixelFormat::YCRCB_420_SP,
        ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_422_I,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_FP16,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RAW16,
        ::android::hardware::graphics::common::V1_0::PixelFormat::BLOB,
        ::android::hardware::graphics::common::V1_0::PixelFormat::IMPLEMENTATION_DEFINED,
        ::android::hardware::graphics::common::V1_0::PixelFormat::YCBCR_420_888,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RAW_OPAQUE,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RAW10,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RAW12,
        ::android::hardware::graphics::common::V1_0::PixelFormat::RGBA_1010102,
        ::android::hardware::graphics::common::V1_0::PixelFormat::Y8,
        ::android::hardware::graphics::common::V1_0::PixelFormat::Y16,
        ::android::hardware::graphics::common::V1_0::PixelFormat::YV12,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_0::BufferUsage, 23>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::BufferUsage> = {
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_MASK,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_NEVER,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_RARELY,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_READ_OFTEN,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_MASK,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_NEVER,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_RARELY,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CPU_WRITE_OFTEN,
        ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_TEXTURE,
        ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_RENDER_TARGET,
        ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_OVERLAY,
        ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CLIENT_TARGET,
        ::android::hardware::graphics::common::V1_0::BufferUsage::PROTECTED,
        ::android::hardware::graphics::common::V1_0::BufferUsage::COMPOSER_CURSOR,
        ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_ENCODER,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_OUTPUT,
        ::android::hardware::graphics::common::V1_0::BufferUsage::CAMERA_INPUT,
        ::android::hardware::graphics::common::V1_0::BufferUsage::RENDERSCRIPT,
        ::android::hardware::graphics::common::V1_0::BufferUsage::VIDEO_DECODER,
        ::android::hardware::graphics::common::V1_0::BufferUsage::SENSOR_DIRECT_DATA,
        ::android::hardware::graphics::common::V1_0::BufferUsage::GPU_DATA_BUFFER,
        ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK,
        ::android::hardware::graphics::common::V1_0::BufferUsage::VENDOR_MASK_HI,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_0::Transform, 5>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::Transform> = {
        ::android::hardware::graphics::common::V1_0::Transform::FLIP_H,
        ::android::hardware::graphics::common::V1_0::Transform::FLIP_V,
        ::android::hardware::graphics::common::V1_0::Transform::ROT_90,
        ::android::hardware::graphics::common::V1_0::Transform::ROT_180,
        ::android::hardware::graphics::common::V1_0::Transform::ROT_270,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_0::Dataspace, 57>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::Dataspace> = {
        ::android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN,
        ::android::hardware::graphics::common::V1_0::Dataspace::ARBITRARY,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_SHIFT,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_MASK,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_UNSPECIFIED,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT709,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_625_UNADJUSTED,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT601_525_UNADJUSTED,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT2020_CONSTANT_LUMINANCE,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_BT470M,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_FILM,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_DCI_P3,
        ::android::hardware::graphics::common::V1_0::Dataspace::STANDARD_ADOBE_RGB,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SHIFT,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_MASK,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_UNSPECIFIED,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SRGB,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_SMPTE_170M,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_2,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_6,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_GAMMA2_8,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_ST2084,
        ::android::hardware::graphics::common::V1_0::Dataspace::TRANSFER_HLG,
        ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_SHIFT,
        ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_MASK,
        ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_UNSPECIFIED,
        ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_FULL,
        ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_LIMITED,
        ::android::hardware::graphics::common::V1_0::Dataspace::RANGE_EXTENDED,
        ::android::hardware::graphics::common::V1_0::Dataspace::SRGB_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::SRGB,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_SRGB,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_SCRGB,
        ::android::hardware::graphics::common::V1_0::Dataspace::JFIF,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_JFIF,
        ::android::hardware::graphics::common::V1_0::Dataspace::BT601_625,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_625,
        ::android::hardware::graphics::common::V1_0::Dataspace::BT601_525,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT601_525,
        ::android::hardware::graphics::common::V1_0::Dataspace::BT709,
        ::android::hardware::graphics::common::V1_0::Dataspace::V0_BT709,
        ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::DCI_P3,
        ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::DISPLAY_P3,
        ::android::hardware::graphics::common::V1_0::Dataspace::ADOBE_RGB,
        ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_LINEAR,
        ::android::hardware::graphics::common::V1_0::Dataspace::BT2020,
        ::android::hardware::graphics::common::V1_0::Dataspace::BT2020_PQ,
        ::android::hardware::graphics::common::V1_0::Dataspace::DEPTH,
        ::android::hardware::graphics::common::V1_0::Dataspace::SENSOR,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_0::ColorMode, 10>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::ColorMode> = {
        ::android::hardware::graphics::common::V1_0::ColorMode::NATIVE,
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625,
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_625_UNADJUSTED,
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525,
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT601_525_UNADJUSTED,
        ::android::hardware::graphics::common::V1_0::ColorMode::STANDARD_BT709,
        ::android::hardware::graphics::common::V1_0::ColorMode::DCI_P3,
        ::android::hardware::graphics::common::V1_0::ColorMode::SRGB,
        ::android::hardware::graphics::common::V1_0::ColorMode::ADOBE_RGB,
        ::android::hardware::graphics::common::V1_0::ColorMode::DISPLAY_P3,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_0::ColorTransform, 7>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::ColorTransform> = {
        ::android::hardware::graphics::common::V1_0::ColorTransform::IDENTITY,
        ::android::hardware::graphics::common::V1_0::ColorTransform::ARBITRARY_MATRIX,
        ::android::hardware::graphics::common::V1_0::ColorTransform::VALUE_INVERSE,
        ::android::hardware::graphics::common::V1_0::ColorTransform::GRAYSCALE,
        ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_PROTANOPIA,
        ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_DEUTERANOPIA,
        ::android::hardware::graphics::common::V1_0::ColorTransform::CORRECT_TRITANOPIA,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_0::Hdr, 3>
    hidl_enum_values<::android::hardware::graphics::common::V1_0::Hdr> = {
        ::android::hardware::graphics::common::V1_0::Hdr::DOLBY_VISION,
        ::android::hardware::graphics::common::V1_0::Hdr::HDR10,
        ::android::hardware::graphics::common::V1_0::Hdr::HLG,
};
}
}
}

#endif

#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_COMMON_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_COMMON_V1_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace graphics {
namespace common {
namespace V1_1 {

enum class PixelFormat : int32_t;
enum class BufferUsage : uint64_t;
enum class Dataspace : int32_t;
enum class ColorMode : int32_t;
enum class RenderIntent : int32_t;

enum class PixelFormat : int32_t {







  RGBA_8888 = 1  ,







  RGBX_8888 = 2  ,







  RGB_888 = 3  ,








  RGB_565 = 4  ,







  BGRA_8888 = 5  ,



  YCBCR_422_SP = 16  ,
  YCRCB_420_SP = 17  ,
  YCBCR_422_I = 20  ,







  RGBA_FP16 = 22  ,









































  RAW16 = 32  ,





















  BLOB = 33  ,














  IMPLEMENTATION_DEFINED = 34  ,















  YCBCR_420_888 = 35  ,




















  RAW_OPAQUE = 36  ,





















































  RAW10 = 37  ,

















































  RAW12 = 38  ,











  RGBA_1010102 = 43  ,





























  Y8 = 538982489  ,

























  Y16 = 540422489  ,































  YV12 = 842094169  ,






  DEPTH_16 = 48  ,







  DEPTH_24 = 49  ,








  DEPTH_24_STENCIL_8 = 50  ,






  DEPTH_32F = 51  ,








  DEPTH_32F_STENCIL_8 = 52  ,






  STENCIL_8 = 53  ,































  YCBCR_P010 = 54  ,
};

enum class BufferUsage : uint64_t {



  CPU_READ_MASK = 15ull  ,



  CPU_READ_NEVER = 0ull,
  CPU_READ_RARELY = 2ull,
  CPU_READ_OFTEN = 3ull,



  CPU_WRITE_MASK = 240ull  ,



  CPU_WRITE_NEVER = 0ull  ,



  CPU_WRITE_RARELY = 32ull  ,



  CPU_WRITE_OFTEN = 48ull  ,



  GPU_TEXTURE = 256ull  ,



  GPU_RENDER_TARGET = 512ull  ,






  COMPOSER_OVERLAY = 2048ull  ,



  COMPOSER_CLIENT_TARGET = 4096ull  ,








  PROTECTED = 16384ull  ,



  COMPOSER_CURSOR = 32768ull  ,



  VIDEO_ENCODER = 65536ull  ,



  CAMERA_OUTPUT = 131072ull  ,



  CAMERA_INPUT = 262144ull  ,






  RENDERSCRIPT = 1048576ull  ,






  VIDEO_DECODER = 4194304ull  ,



  SENSOR_DIRECT_DATA = 8388608ull  ,




  GPU_DATA_BUFFER = 16777216ull  ,






  VENDOR_MASK = 4026531840ull  ,






  VENDOR_MASK_HI = 18446462598732840960ull  ,



  GPU_CUBE_MAP = 33554432ull  ,



  GPU_MIPMAP_COMPLETE = 67108864ull  ,
};

enum class Dataspace : int32_t {









  UNKNOWN = 0  ,











  ARBITRARY = 1  ,







  STANDARD_SHIFT = 16,






  STANDARD_MASK = 4128768  ,













  STANDARD_UNSPECIFIED = 0  ,










  STANDARD_BT709 = 65536  ,












  STANDARD_BT601_625 = 131072  ,










  STANDARD_BT601_625_UNADJUSTED = 196608  ,












  STANDARD_BT601_525 = 262144  ,










  STANDARD_BT601_525_UNADJUSTED = 327680  ,










  STANDARD_BT2020 = 393216  ,










  STANDARD_BT2020_CONSTANT_LUMINANCE = 458752  ,










  STANDARD_BT470M = 524288  ,










  STANDARD_FILM = 589824  ,








  STANDARD_DCI_P3 = 655360  ,








  STANDARD_ADOBE_RGB = 720896  ,
  TRANSFER_SHIFT = 22,













  TRANSFER_MASK = 130023424  ,












  TRANSFER_UNSPECIFIED = 0  ,






  TRANSFER_LINEAR = 4194304  ,








  TRANSFER_SRGB = 8388608  ,









  TRANSFER_SMPTE_170M = 12582912  ,








  TRANSFER_GAMMA2_2 = 16777216  ,








  TRANSFER_GAMMA2_6 = 20971520  ,








  TRANSFER_GAMMA2_8 = 25165824  ,














  TRANSFER_ST2084 = 29360128  ,














  TRANSFER_HLG = 33554432  ,
  RANGE_SHIFT = 27,






  RANGE_MASK = 939524096  ,











  RANGE_UNSPECIFIED = 0  ,




  RANGE_FULL = 134217728  ,













  RANGE_LIMITED = 268435456  ,







  RANGE_EXTENDED = 402653184  ,













  SRGB_LINEAR = 512  ,
  V0_SRGB_LINEAR = 138477568  ,












  V0_SCRGB_LINEAR = 406913024  ,













  SRGB = 513  ,
  V0_SRGB = 142671872  ,












  V0_SCRGB = 411107328  ,

















  JFIF = 257  ,
  V0_JFIF = 146931712  ,







  BT601_625 = 258  ,
  V0_BT601_625 = 281149440  ,







  BT601_525 = 259  ,
  V0_BT601_525 = 281280512  ,







  BT709 = 260  ,
  V0_BT709 = 281083904  ,







  DCI_P3_LINEAR = 139067392  ,









  DCI_P3 = 155844608  ,






  DISPLAY_P3_LINEAR = 139067392  ,






  DISPLAY_P3 = 143261696  ,







  ADOBE_RGB = 151715840  ,







  BT2020_LINEAR = 138805248  ,







  BT2020 = 147193856  ,







  BT2020_PQ = 163971072  ,





















  DEPTH = 4096  ,







  SENSOR = 4097  ,







  BT2020_ITU = 281411584  ,








  BT2020_ITU_PQ = 298188800  ,
  BT2020_ITU_HLG = 302383104  ,
  BT2020_HLG = 168165376  ,
};

enum class ColorMode : int32_t {
  NATIVE = 0,
  STANDARD_BT601_625 = 1,
  STANDARD_BT601_625_UNADJUSTED = 2,
  STANDARD_BT601_525 = 3,
  STANDARD_BT601_525_UNADJUSTED = 4,
  STANDARD_BT709 = 5,
  DCI_P3 = 6,
  SRGB = 7,
  ADOBE_RGB = 8,
  DISPLAY_P3 = 9,
  BT2020 = 10,
  BT2100_PQ = 11,
  BT2100_HLG = 12,
};

enum class RenderIntent : int32_t {
  COLORIMETRIC = 0,
  ENHANCE = 1,
  TONE_MAP_COLORIMETRIC = 2,
  TONE_MAP_ENHANCE = 3,
};


template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_1::PixelFormat o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::PixelFormat lhs,
                            const ::android::hardware::graphics::common::V1_1::PixelFormat rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::PixelFormat rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::PixelFormat lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::PixelFormat lhs,
                            const ::android::hardware::graphics::common::V1_1::PixelFormat rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::PixelFormat rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::PixelFormat lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::PixelFormat e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::PixelFormat e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_1::BufferUsage o);

constexpr uint64_t operator|(const ::android::hardware::graphics::common::V1_1::BufferUsage lhs,
                             const ::android::hardware::graphics::common::V1_1::BufferUsage rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs,
                             const ::android::hardware::graphics::common::V1_1::BufferUsage rhs) {
  return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::graphics::common::V1_1::BufferUsage lhs,
                             const uint64_t rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::graphics::common::V1_1::BufferUsage lhs,
                             const ::android::hardware::graphics::common::V1_1::BufferUsage rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs,
                             const ::android::hardware::graphics::common::V1_1::BufferUsage rhs) {
  return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::graphics::common::V1_1::BufferUsage lhs,
                             const uint64_t rhs) {
  return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t& operator|=(uint64_t& v,
                               const ::android::hardware::graphics::common::V1_1::BufferUsage e) {
  v |= static_cast<uint64_t>(e);
  return v;
}
constexpr uint64_t& operator&=(uint64_t& v,
                               const ::android::hardware::graphics::common::V1_1::BufferUsage e) {
  v &= static_cast<uint64_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_1::Dataspace o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::Dataspace lhs,
                            const ::android::hardware::graphics::common::V1_1::Dataspace rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::Dataspace rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::Dataspace lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::Dataspace lhs,
                            const ::android::hardware::graphics::common::V1_1::Dataspace rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::Dataspace rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::Dataspace lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::Dataspace e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::Dataspace e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_1::ColorMode o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::ColorMode lhs,
                            const ::android::hardware::graphics::common::V1_1::ColorMode rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::ColorMode rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::ColorMode lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::ColorMode lhs,
                            const ::android::hardware::graphics::common::V1_1::ColorMode rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::ColorMode rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::ColorMode lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::ColorMode e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::ColorMode e) {
  v &= static_cast<int32_t>(e);
  return v;
}

template <typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::graphics::common::V1_1::RenderIntent o);

constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::RenderIntent lhs,
                            const ::android::hardware::graphics::common::V1_1::RenderIntent rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::RenderIntent rhs) {
  return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::graphics::common::V1_1::RenderIntent lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::RenderIntent lhs,
                            const ::android::hardware::graphics::common::V1_1::RenderIntent rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs,
                            const ::android::hardware::graphics::common::V1_1::RenderIntent rhs) {
  return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::graphics::common::V1_1::RenderIntent lhs,
                            const int32_t rhs) {
  return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t& operator|=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::RenderIntent e) {
  v |= static_cast<int32_t>(e);
  return v;
}
constexpr int32_t& operator&=(int32_t& v,
                              const ::android::hardware::graphics::common::V1_1::RenderIntent e) {
  v &= static_cast<int32_t>(e);
  return v;
}


template <>
inline std::string toString<::android::hardware::graphics::common::V1_1::PixelFormat>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_1::PixelFormat>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_8888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_8888)) {
    os += (first ? "" : " | ");
    os += "RGBA_8888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_8888;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RGBX_8888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RGBX_8888)) {
    os += (first ? "" : " | ");
    os += "RGBX_8888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RGBX_8888;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RGB_888)) {
    os += (first ? "" : " | ");
    os += "RGB_888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_888;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_565) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RGB_565)) {
    os += (first ? "" : " | ");
    os += "RGB_565";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_565;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::BGRA_8888) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::BGRA_8888)) {
    os += (first ? "" : " | ");
    os += "BGRA_8888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::BGRA_8888;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_SP) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_SP)) {
    os += (first ? "" : " | ");
    os += "YCBCR_422_SP";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_SP;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::YCRCB_420_SP) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::YCRCB_420_SP)) {
    os += (first ? "" : " | ");
    os += "YCRCB_420_SP";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::YCRCB_420_SP;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_I) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_I)) {
    os += (first ? "" : " | ");
    os += "YCBCR_422_I";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_I;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_FP16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_FP16)) {
    os += (first ? "" : " | ");
    os += "RGBA_FP16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_FP16;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RAW16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RAW16)) {
    os += (first ? "" : " | ");
    os += "RAW16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RAW16;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::BLOB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::BLOB)) {
    os += (first ? "" : " | ");
    os += "BLOB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::BLOB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::IMPLEMENTATION_DEFINED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::IMPLEMENTATION_DEFINED)) {
    os += (first ? "" : " | ");
    os += "IMPLEMENTATION_DEFINED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::IMPLEMENTATION_DEFINED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_420_888) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_420_888)) {
    os += (first ? "" : " | ");
    os += "YCBCR_420_888";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_420_888;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RAW_OPAQUE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RAW_OPAQUE)) {
    os += (first ? "" : " | ");
    os += "RAW_OPAQUE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RAW_OPAQUE;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RAW10) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RAW10)) {
    os += (first ? "" : " | ");
    os += "RAW10";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RAW10;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RAW12) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::RAW12)) {
    os += (first ? "" : " | ");
    os += "RAW12";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RAW12;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_1010102) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_1010102)) {
    os += (first ? "" : " | ");
    os += "RGBA_1010102";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_1010102;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::Y8) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::Y8)) {
    os += (first ? "" : " | ");
    os += "Y8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::Y8;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::Y16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::Y16)) {
    os += (first ? "" : " | ");
    os += "Y16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::Y16;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::YV12) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::YV12)) {
    os += (first ? "" : " | ");
    os += "YV12";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::YV12;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_16) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_16)) {
    os += (first ? "" : " | ");
    os += "DEPTH_16";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_16;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24)) {
    os += (first ? "" : " | ");
    os += "DEPTH_24";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24_STENCIL_8) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24_STENCIL_8)) {
    os += (first ? "" : " | ");
    os += "DEPTH_24_STENCIL_8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24_STENCIL_8;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F)) {
    os += (first ? "" : " | ");
    os += "DEPTH_32F";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F_STENCIL_8) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F_STENCIL_8)) {
    os += (first ? "" : " | ");
    os += "DEPTH_32F_STENCIL_8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F_STENCIL_8;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::STENCIL_8) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::STENCIL_8)) {
    os += (first ? "" : " | ");
    os += "STENCIL_8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::STENCIL_8;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_P010) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_P010)) {
    os += (first ? "" : " | ");
    os += "YCBCR_P010";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_P010;
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

static inline std::string toString(::android::hardware::graphics::common::V1_1::PixelFormat o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_8888) {
    return "RGBA_8888";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RGBX_8888) {
    return "RGBX_8888";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_888) {
    return "RGB_888";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_565) {
    return "RGB_565";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::BGRA_8888) {
    return "BGRA_8888";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_SP) {
    return "YCBCR_422_SP";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::YCRCB_420_SP) {
    return "YCRCB_420_SP";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_I) {
    return "YCBCR_422_I";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_FP16) {
    return "RGBA_FP16";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RAW16) {
    return "RAW16";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::BLOB) {
    return "BLOB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::IMPLEMENTATION_DEFINED) {
    return "IMPLEMENTATION_DEFINED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_420_888) {
    return "YCBCR_420_888";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RAW_OPAQUE) {
    return "RAW_OPAQUE";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RAW10) {
    return "RAW10";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RAW12) {
    return "RAW12";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_1010102) {
    return "RGBA_1010102";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::Y8) {
    return "Y8";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::Y16) {
    return "Y16";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::YV12) {
    return "YV12";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_16) {
    return "DEPTH_16";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24) {
    return "DEPTH_24";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24_STENCIL_8) {
    return "DEPTH_24_STENCIL_8";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F) {
    return "DEPTH_32F";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F_STENCIL_8) {
    return "DEPTH_32F_STENCIL_8";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::STENCIL_8) {
    return "STENCIL_8";
  }
  if (o == ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_P010) {
    return "YCBCR_P010";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_1::BufferUsage>(uint64_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_1::BufferUsage>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_MASK) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_MASK)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_NEVER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_NEVER)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_NEVER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_NEVER;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_RARELY) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_RARELY)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_RARELY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_RARELY;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_OFTEN) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_OFTEN)) {
    os += (first ? "" : " | ");
    os += "CPU_READ_OFTEN";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_OFTEN;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_MASK) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_MASK)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_NEVER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_NEVER)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_NEVER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_NEVER;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_RARELY) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_RARELY)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_RARELY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_RARELY;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_OFTEN) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_OFTEN)) {
    os += (first ? "" : " | ");
    os += "CPU_WRITE_OFTEN";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_OFTEN;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_TEXTURE) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_TEXTURE)) {
    os += (first ? "" : " | ");
    os += "GPU_TEXTURE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_TEXTURE;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_RENDER_TARGET) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_RENDER_TARGET)) {
    os += (first ? "" : " | ");
    os += "GPU_RENDER_TARGET";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_RENDER_TARGET;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_OVERLAY) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_OVERLAY)) {
    os += (first ? "" : " | ");
    os += "COMPOSER_OVERLAY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_OVERLAY;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CLIENT_TARGET) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CLIENT_TARGET)) {
    os += (first ? "" : " | ");
    os += "COMPOSER_CLIENT_TARGET";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CLIENT_TARGET;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::PROTECTED) ==
      static_cast<uint64_t>(::android::hardware::graphics::common::V1_1::BufferUsage::PROTECTED)) {
    os += (first ? "" : " | ");
    os += "PROTECTED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::PROTECTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CURSOR) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CURSOR)) {
    os += (first ? "" : " | ");
    os += "COMPOSER_CURSOR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CURSOR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_ENCODER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_ENCODER)) {
    os += (first ? "" : " | ");
    os += "VIDEO_ENCODER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_ENCODER;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_OUTPUT) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_OUTPUT)) {
    os += (first ? "" : " | ");
    os += "CAMERA_OUTPUT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_OUTPUT;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_INPUT) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_INPUT)) {
    os += (first ? "" : " | ");
    os += "CAMERA_INPUT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_INPUT;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::RENDERSCRIPT) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::RENDERSCRIPT)) {
    os += (first ? "" : " | ");
    os += "RENDERSCRIPT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::RENDERSCRIPT;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_DECODER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_DECODER)) {
    os += (first ? "" : " | ");
    os += "VIDEO_DECODER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_DECODER;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::SENSOR_DIRECT_DATA) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::SENSOR_DIRECT_DATA)) {
    os += (first ? "" : " | ");
    os += "SENSOR_DIRECT_DATA";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::SENSOR_DIRECT_DATA;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_DATA_BUFFER) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_DATA_BUFFER)) {
    os += (first ? "" : " | ");
    os += "GPU_DATA_BUFFER";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_DATA_BUFFER;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK)) {
    os += (first ? "" : " | ");
    os += "VENDOR_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK_HI) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK_HI)) {
    os += (first ? "" : " | ");
    os += "VENDOR_MASK_HI";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK_HI;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_CUBE_MAP) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_CUBE_MAP)) {
    os += (first ? "" : " | ");
    os += "GPU_CUBE_MAP";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_CUBE_MAP;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_MIPMAP_COMPLETE) ==
      static_cast<uint64_t>(
          ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_MIPMAP_COMPLETE)) {
    os += (first ? "" : " | ");
    os += "GPU_MIPMAP_COMPLETE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_MIPMAP_COMPLETE;
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

static inline std::string toString(::android::hardware::graphics::common::V1_1::BufferUsage o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_MASK) {
    return "CPU_READ_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_NEVER) {
    return "CPU_READ_NEVER";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_RARELY) {
    return "CPU_READ_RARELY";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_OFTEN) {
    return "CPU_READ_OFTEN";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_MASK) {
    return "CPU_WRITE_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_NEVER) {
    return "CPU_WRITE_NEVER";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_RARELY) {
    return "CPU_WRITE_RARELY";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_OFTEN) {
    return "CPU_WRITE_OFTEN";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_TEXTURE) {
    return "GPU_TEXTURE";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_RENDER_TARGET) {
    return "GPU_RENDER_TARGET";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_OVERLAY) {
    return "COMPOSER_OVERLAY";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CLIENT_TARGET) {
    return "COMPOSER_CLIENT_TARGET";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::PROTECTED) {
    return "PROTECTED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CURSOR) {
    return "COMPOSER_CURSOR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_ENCODER) {
    return "VIDEO_ENCODER";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_OUTPUT) {
    return "CAMERA_OUTPUT";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_INPUT) {
    return "CAMERA_INPUT";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::RENDERSCRIPT) {
    return "RENDERSCRIPT";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_DECODER) {
    return "VIDEO_DECODER";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::SENSOR_DIRECT_DATA) {
    return "SENSOR_DIRECT_DATA";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_DATA_BUFFER) {
    return "GPU_DATA_BUFFER";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK) {
    return "VENDOR_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK_HI) {
    return "VENDOR_MASK_HI";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_CUBE_MAP) {
    return "GPU_CUBE_MAP";
  }
  if (o == ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_MIPMAP_COMPLETE) {
    return "GPU_MIPMAP_COMPLETE";
  }
  std::string os;
  os += toHexString(static_cast<uint64_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_1::Dataspace>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_1::Dataspace>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::UNKNOWN) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::UNKNOWN)) {
    os += (first ? "" : " | ");
    os += "UNKNOWN";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::UNKNOWN;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::ARBITRARY) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::ARBITRARY)) {
    os += (first ? "" : " | ");
    os += "ARBITRARY";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::ARBITRARY;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_SHIFT) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_SHIFT)) {
    os += (first ? "" : " | ");
    os += "STANDARD_SHIFT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_SHIFT;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_MASK) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_MASK)) {
    os += (first ? "" : " | ");
    os += "STANDARD_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_UNSPECIFIED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_UNSPECIFIED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_UNSPECIFIED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_UNSPECIFIED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT709) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT709)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT2020";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::
               STANDARD_BT2020_CONSTANT_LUMINANCE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::
                               STANDARD_BT2020_CONSTANT_LUMINANCE)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT2020_CONSTANT_LUMINANCE";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020_CONSTANT_LUMINANCE;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT470M) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT470M)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT470M";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT470M;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_FILM) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_FILM)) {
    os += (first ? "" : " | ");
    os += "STANDARD_FILM";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_FILM;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_DCI_P3) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_DCI_P3)) {
    os += (first ? "" : " | ");
    os += "STANDARD_DCI_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_DCI_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_ADOBE_RGB) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_ADOBE_RGB)) {
    os += (first ? "" : " | ");
    os += "STANDARD_ADOBE_RGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_ADOBE_RGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SHIFT) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SHIFT)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_SHIFT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SHIFT;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_MASK) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_MASK)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_UNSPECIFIED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_UNSPECIFIED)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_UNSPECIFIED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_UNSPECIFIED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_LINEAR)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SRGB)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SMPTE_170M) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SMPTE_170M)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_SMPTE_170M";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SMPTE_170M;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_2) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_2)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_GAMMA2_2";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_2;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_6) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_6)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_GAMMA2_6";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_6;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_8) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_8)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_GAMMA2_8";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_8;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_ST2084) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_ST2084)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_ST2084";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_ST2084;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_HLG) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_HLG)) {
    os += (first ? "" : " | ");
    os += "TRANSFER_HLG";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_HLG;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_SHIFT) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::RANGE_SHIFT)) {
    os += (first ? "" : " | ");
    os += "RANGE_SHIFT";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_SHIFT;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_MASK) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::RANGE_MASK)) {
    os += (first ? "" : " | ");
    os += "RANGE_MASK";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_MASK;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_UNSPECIFIED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_UNSPECIFIED)) {
    os += (first ? "" : " | ");
    os += "RANGE_UNSPECIFIED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_UNSPECIFIED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_FULL) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::RANGE_FULL)) {
    os += (first ? "" : " | ");
    os += "RANGE_FULL";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_FULL;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_LIMITED) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::RANGE_LIMITED)) {
    os += (first ? "" : " | ");
    os += "RANGE_LIMITED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_LIMITED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_EXTENDED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_EXTENDED)) {
    os += (first ? "" : " | ");
    os += "RANGE_EXTENDED";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_EXTENDED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::SRGB_LINEAR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::SRGB_LINEAR)) {
    os += (first ? "" : " | ");
    os += "SRGB_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::SRGB_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB_LINEAR)) {
    os += (first ? "" : " | ");
    os += "V0_SRGB_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB_LINEAR)) {
    os += (first ? "" : " | ");
    os += "V0_SCRGB_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::SRGB)) {
    os += (first ? "" : " | ");
    os += "SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB)) {
    os += (first ? "" : " | ");
    os += "V0_SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB)) {
    os += (first ? "" : " | ");
    os += "V0_SCRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::JFIF) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::JFIF)) {
    os += (first ? "" : " | ");
    os += "JFIF";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::JFIF;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_JFIF) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::V0_JFIF)) {
    os += (first ? "" : " | ");
    os += "V0_JFIF";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_JFIF;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT601_625) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT601_625)) {
    os += (first ? "" : " | ");
    os += "BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_625) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_625)) {
    os += (first ? "" : " | ");
    os += "V0_BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT601_525) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT601_525)) {
    os += (first ? "" : " | ");
    os += "BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_525) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_525)) {
    os += (first ? "" : " | ");
    os += "V0_BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT709) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT709)) {
    os += (first ? "" : " | ");
    os += "BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT709) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::V0_BT709)) {
    os += (first ? "" : " | ");
    os += "V0_BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3_LINEAR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3_LINEAR)) {
    os += (first ? "" : " | ");
    os += "DCI_P3_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3)) {
    os += (first ? "" : " | ");
    os += "DCI_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3_LINEAR) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3_LINEAR)) {
    os += (first ? "" : " | ");
    os += "DISPLAY_P3_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3)) {
    os += (first ? "" : " | ");
    os += "DISPLAY_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::ADOBE_RGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::ADOBE_RGB)) {
    os += (first ? "" : " | ");
    os += "ADOBE_RGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::ADOBE_RGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_LINEAR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT2020_LINEAR)) {
    os += (first ? "" : " | ");
    os += "BT2020_LINEAR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_LINEAR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT2020)) {
    os += (first ? "" : " | ");
    os += "BT2020";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_PQ) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT2020_PQ)) {
    os += (first ? "" : " | ");
    os += "BT2020_PQ";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_PQ;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::DEPTH) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::DEPTH)) {
    os += (first ? "" : " | ");
    os += "DEPTH";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::DEPTH;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::SENSOR) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::SENSOR)) {
    os += (first ? "" : " | ");
    os += "SENSOR";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::SENSOR;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU)) {
    os += (first ? "" : " | ");
    os += "BT2020_ITU";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_PQ) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_PQ)) {
    os += (first ? "" : " | ");
    os += "BT2020_ITU_PQ";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_PQ;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_HLG) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_HLG)) {
    os += (first ? "" : " | ");
    os += "BT2020_ITU_HLG";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_HLG;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_HLG) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::Dataspace::BT2020_HLG)) {
    os += (first ? "" : " | ");
    os += "BT2020_HLG";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_HLG;
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

static inline std::string toString(::android::hardware::graphics::common::V1_1::Dataspace o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::UNKNOWN) {
    return "UNKNOWN";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::ARBITRARY) {
    return "ARBITRARY";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_SHIFT) {
    return "STANDARD_SHIFT";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_MASK) {
    return "STANDARD_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_UNSPECIFIED) {
    return "STANDARD_UNSPECIFIED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT709) {
    return "STANDARD_BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625) {
    return "STANDARD_BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625_UNADJUSTED) {
    return "STANDARD_BT601_625_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525) {
    return "STANDARD_BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525_UNADJUSTED) {
    return "STANDARD_BT601_525_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020) {
    return "STANDARD_BT2020";
  }
  if (o ==
      ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020_CONSTANT_LUMINANCE) {
    return "STANDARD_BT2020_CONSTANT_LUMINANCE";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT470M) {
    return "STANDARD_BT470M";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_FILM) {
    return "STANDARD_FILM";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_DCI_P3) {
    return "STANDARD_DCI_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_ADOBE_RGB) {
    return "STANDARD_ADOBE_RGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SHIFT) {
    return "TRANSFER_SHIFT";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_MASK) {
    return "TRANSFER_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_UNSPECIFIED) {
    return "TRANSFER_UNSPECIFIED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_LINEAR) {
    return "TRANSFER_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SRGB) {
    return "TRANSFER_SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SMPTE_170M) {
    return "TRANSFER_SMPTE_170M";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_2) {
    return "TRANSFER_GAMMA2_2";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_6) {
    return "TRANSFER_GAMMA2_6";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_8) {
    return "TRANSFER_GAMMA2_8";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_ST2084) {
    return "TRANSFER_ST2084";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_HLG) {
    return "TRANSFER_HLG";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_SHIFT) {
    return "RANGE_SHIFT";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_MASK) {
    return "RANGE_MASK";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_UNSPECIFIED) {
    return "RANGE_UNSPECIFIED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_FULL) {
    return "RANGE_FULL";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_LIMITED) {
    return "RANGE_LIMITED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_EXTENDED) {
    return "RANGE_EXTENDED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::SRGB_LINEAR) {
    return "SRGB_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB_LINEAR) {
    return "V0_SRGB_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB_LINEAR) {
    return "V0_SCRGB_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::SRGB) {
    return "SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB) {
    return "V0_SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB) {
    return "V0_SCRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::JFIF) {
    return "JFIF";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_JFIF) {
    return "V0_JFIF";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT601_625) {
    return "BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_625) {
    return "V0_BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT601_525) {
    return "BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_525) {
    return "V0_BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT709) {
    return "BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT709) {
    return "V0_BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3_LINEAR) {
    return "DCI_P3_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3) {
    return "DCI_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3_LINEAR) {
    return "DISPLAY_P3_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3) {
    return "DISPLAY_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::ADOBE_RGB) {
    return "ADOBE_RGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_LINEAR) {
    return "BT2020_LINEAR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020) {
    return "BT2020";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_PQ) {
    return "BT2020_PQ";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::DEPTH) {
    return "DEPTH";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::SENSOR) {
    return "SENSOR";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU) {
    return "BT2020_ITU";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_PQ) {
    return "BT2020_ITU_PQ";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_HLG) {
    return "BT2020_ITU_HLG";
  }
  if (o == ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_HLG) {
    return "BT2020_HLG";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_1::ColorMode>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_1::ColorMode>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::NATIVE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::NATIVE)) {
    os += (first ? "" : " | ");
    os += "NATIVE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::NATIVE;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_625_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525_UNADJUSTED) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525_UNADJUSTED)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT601_525_UNADJUSTED";
    first = false;
    flipped |=
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525_UNADJUSTED;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT709) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT709)) {
    os += (first ? "" : " | ");
    os += "STANDARD_BT709";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT709;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::DCI_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::DCI_P3)) {
    os += (first ? "" : " | ");
    os += "DCI_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::DCI_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::SRGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::SRGB)) {
    os += (first ? "" : " | ");
    os += "SRGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::SRGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::ADOBE_RGB) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::ADOBE_RGB)) {
    os += (first ? "" : " | ");
    os += "ADOBE_RGB";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::ADOBE_RGB;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::DISPLAY_P3) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::DISPLAY_P3)) {
    os += (first ? "" : " | ");
    os += "DISPLAY_P3";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::DISPLAY_P3;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::BT2020) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::BT2020)) {
    os += (first ? "" : " | ");
    os += "BT2020";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::BT2020;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_PQ) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::BT2100_PQ)) {
    os += (first ? "" : " | ");
    os += "BT2100_PQ";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_PQ;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_HLG) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::ColorMode::BT2100_HLG)) {
    os += (first ? "" : " | ");
    os += "BT2100_HLG";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_HLG;
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

static inline std::string toString(::android::hardware::graphics::common::V1_1::ColorMode o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::NATIVE) {
    return "NATIVE";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625) {
    return "STANDARD_BT601_625";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625_UNADJUSTED) {
    return "STANDARD_BT601_625_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525) {
    return "STANDARD_BT601_525";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525_UNADJUSTED) {
    return "STANDARD_BT601_525_UNADJUSTED";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT709) {
    return "STANDARD_BT709";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::DCI_P3) {
    return "DCI_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::SRGB) {
    return "SRGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::ADOBE_RGB) {
    return "ADOBE_RGB";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::DISPLAY_P3) {
    return "DISPLAY_P3";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::BT2020) {
    return "BT2020";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_PQ) {
    return "BT2100_PQ";
  }
  if (o == ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_HLG) {
    return "BT2100_HLG";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
}

template <>
inline std::string toString<::android::hardware::graphics::common::V1_1::RenderIntent>(int32_t o) {
  using ::android::hardware::details::toHexString;
  std::string os;
  ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_1::RenderIntent>
      flipped = 0;
  bool first = true;
  if ((o & ::android::hardware::graphics::common::V1_1::RenderIntent::COLORIMETRIC) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::RenderIntent::COLORIMETRIC)) {
    os += (first ? "" : " | ");
    os += "COLORIMETRIC";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::RenderIntent::COLORIMETRIC;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::RenderIntent::ENHANCE) ==
      static_cast<int32_t>(::android::hardware::graphics::common::V1_1::RenderIntent::ENHANCE)) {
    os += (first ? "" : " | ");
    os += "ENHANCE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::RenderIntent::ENHANCE;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_COLORIMETRIC) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_COLORIMETRIC)) {
    os += (first ? "" : " | ");
    os += "TONE_MAP_COLORIMETRIC";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_COLORIMETRIC;
  }
  if ((o & ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_ENHANCE) ==
      static_cast<int32_t>(
          ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_ENHANCE)) {
    os += (first ? "" : " | ");
    os += "TONE_MAP_ENHANCE";
    first = false;
    flipped |= ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_ENHANCE;
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

static inline std::string toString(::android::hardware::graphics::common::V1_1::RenderIntent o) {
  using ::android::hardware::details::toHexString;
  if (o == ::android::hardware::graphics::common::V1_1::RenderIntent::COLORIMETRIC) {
    return "COLORIMETRIC";
  }
  if (o == ::android::hardware::graphics::common::V1_1::RenderIntent::ENHANCE) {
    return "ENHANCE";
  }
  if (o == ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_COLORIMETRIC) {
    return "TONE_MAP_COLORIMETRIC";
  }
  if (o == ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_ENHANCE) {
    return "TONE_MAP_ENHANCE";
  }
  std::string os;
  os += toHexString(static_cast<int32_t>(o));
  return os;
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
constexpr std::array<::android::hardware::graphics::common::V1_1::PixelFormat, 27>
    hidl_enum_values<::android::hardware::graphics::common::V1_1::PixelFormat> = {
        ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_8888,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RGBX_8888,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_888,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RGB_565,
        ::android::hardware::graphics::common::V1_1::PixelFormat::BGRA_8888,
        ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_SP,
        ::android::hardware::graphics::common::V1_1::PixelFormat::YCRCB_420_SP,
        ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_422_I,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_FP16,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RAW16,
        ::android::hardware::graphics::common::V1_1::PixelFormat::BLOB,
        ::android::hardware::graphics::common::V1_1::PixelFormat::IMPLEMENTATION_DEFINED,
        ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_420_888,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RAW_OPAQUE,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RAW10,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RAW12,
        ::android::hardware::graphics::common::V1_1::PixelFormat::RGBA_1010102,
        ::android::hardware::graphics::common::V1_1::PixelFormat::Y8,
        ::android::hardware::graphics::common::V1_1::PixelFormat::Y16,
        ::android::hardware::graphics::common::V1_1::PixelFormat::YV12,
        ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_16,
        ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24,
        ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_24_STENCIL_8,
        ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F,
        ::android::hardware::graphics::common::V1_1::PixelFormat::DEPTH_32F_STENCIL_8,
        ::android::hardware::graphics::common::V1_1::PixelFormat::STENCIL_8,
        ::android::hardware::graphics::common::V1_1::PixelFormat::YCBCR_P010,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_1::BufferUsage, 25>
    hidl_enum_values<::android::hardware::graphics::common::V1_1::BufferUsage> = {
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_MASK,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_NEVER,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_RARELY,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_READ_OFTEN,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_MASK,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_NEVER,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_RARELY,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CPU_WRITE_OFTEN,
        ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_TEXTURE,
        ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_RENDER_TARGET,
        ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_OVERLAY,
        ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CLIENT_TARGET,
        ::android::hardware::graphics::common::V1_1::BufferUsage::PROTECTED,
        ::android::hardware::graphics::common::V1_1::BufferUsage::COMPOSER_CURSOR,
        ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_ENCODER,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_OUTPUT,
        ::android::hardware::graphics::common::V1_1::BufferUsage::CAMERA_INPUT,
        ::android::hardware::graphics::common::V1_1::BufferUsage::RENDERSCRIPT,
        ::android::hardware::graphics::common::V1_1::BufferUsage::VIDEO_DECODER,
        ::android::hardware::graphics::common::V1_1::BufferUsage::SENSOR_DIRECT_DATA,
        ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_DATA_BUFFER,
        ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK,
        ::android::hardware::graphics::common::V1_1::BufferUsage::VENDOR_MASK_HI,
        ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_CUBE_MAP,
        ::android::hardware::graphics::common::V1_1::BufferUsage::GPU_MIPMAP_COMPLETE,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_1::Dataspace, 61>
    hidl_enum_values<::android::hardware::graphics::common::V1_1::Dataspace> = {
        ::android::hardware::graphics::common::V1_1::Dataspace::UNKNOWN,
        ::android::hardware::graphics::common::V1_1::Dataspace::ARBITRARY,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_SHIFT,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_MASK,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_UNSPECIFIED,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT709,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_625_UNADJUSTED,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT601_525_UNADJUSTED,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT2020_CONSTANT_LUMINANCE,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_BT470M,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_FILM,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_DCI_P3,
        ::android::hardware::graphics::common::V1_1::Dataspace::STANDARD_ADOBE_RGB,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SHIFT,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_MASK,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_UNSPECIFIED,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SRGB,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_SMPTE_170M,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_2,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_6,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_GAMMA2_8,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_ST2084,
        ::android::hardware::graphics::common::V1_1::Dataspace::TRANSFER_HLG,
        ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_SHIFT,
        ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_MASK,
        ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_UNSPECIFIED,
        ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_FULL,
        ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_LIMITED,
        ::android::hardware::graphics::common::V1_1::Dataspace::RANGE_EXTENDED,
        ::android::hardware::graphics::common::V1_1::Dataspace::SRGB_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::SRGB,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_SRGB,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_SCRGB,
        ::android::hardware::graphics::common::V1_1::Dataspace::JFIF,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_JFIF,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT601_625,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_625,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT601_525,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT601_525,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT709,
        ::android::hardware::graphics::common::V1_1::Dataspace::V0_BT709,
        ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::DCI_P3,
        ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::DISPLAY_P3,
        ::android::hardware::graphics::common::V1_1::Dataspace::ADOBE_RGB,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_LINEAR,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_PQ,
        ::android::hardware::graphics::common::V1_1::Dataspace::DEPTH,
        ::android::hardware::graphics::common::V1_1::Dataspace::SENSOR,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_PQ,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_ITU_HLG,
        ::android::hardware::graphics::common::V1_1::Dataspace::BT2020_HLG,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_1::ColorMode, 13>
    hidl_enum_values<::android::hardware::graphics::common::V1_1::ColorMode> = {
        ::android::hardware::graphics::common::V1_1::ColorMode::NATIVE,
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625,
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_625_UNADJUSTED,
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525,
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT601_525_UNADJUSTED,
        ::android::hardware::graphics::common::V1_1::ColorMode::STANDARD_BT709,
        ::android::hardware::graphics::common::V1_1::ColorMode::DCI_P3,
        ::android::hardware::graphics::common::V1_1::ColorMode::SRGB,
        ::android::hardware::graphics::common::V1_1::ColorMode::ADOBE_RGB,
        ::android::hardware::graphics::common::V1_1::ColorMode::DISPLAY_P3,
        ::android::hardware::graphics::common::V1_1::ColorMode::BT2020,
        ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_PQ,
        ::android::hardware::graphics::common::V1_1::ColorMode::BT2100_HLG,
};
}
}
}

namespace android {
namespace hardware {
namespace details {
template <>
constexpr std::array<::android::hardware::graphics::common::V1_1::RenderIntent, 4>
    hidl_enum_values<::android::hardware::graphics::common::V1_1::RenderIntent> = {
        ::android::hardware::graphics::common::V1_1::RenderIntent::COLORIMETRIC,
        ::android::hardware::graphics::common::V1_1::RenderIntent::ENHANCE,
        ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_COLORIMETRIC,
        ::android::hardware::graphics::common::V1_1::RenderIntent::TONE_MAP_ENHANCE,
};
}
}
}

#endif
