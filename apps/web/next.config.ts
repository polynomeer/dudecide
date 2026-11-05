import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  typedRoutes: true,
  // Docker runs the minimal server from .next/standalone.
  output: "standalone",
};

export default nextConfig;
