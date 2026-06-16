import React from 'react';

/**
 * Skeleton component mimicking a single Package Card on the Home grid.
 */
export const PackageCardSkeleton = () => {
  return (
    <div className="flex flex-col bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden pointer-events-none">
      {/* Thumbnail area skeleton */}
      <div className="relative h-[200px] flex-shrink-0 animate-shimmer bg-gray-200">
        {/* Top-left Badge skeleton */}
        <div className="absolute top-3 left-3 h-5 w-16 bg-gray-300/40 rounded-full animate-shimmer"></div>
        {/* Top-right Badge skeleton */}
        <div className="absolute top-3 right-3 h-5 w-12 bg-gray-300/30 rounded-full animate-shimmer"></div>
      </div>

      {/* Body area skeleton */}
      <div className="flex flex-col flex-1 p-5 space-y-4">
        {/* Destination + duration row */}
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-1.5 w-1/3">
            <div className="h-3.5 w-3.5 bg-gray-200 rounded-full flex-shrink-0 animate-shimmer"></div>
            <div className="h-3.5 w-full bg-gray-200 rounded animate-shimmer"></div>
          </div>
          <div className="flex items-center space-x-1.5 w-1/4 justify-end">
            <div className="h-3.5 w-3.5 bg-gray-200 rounded-full flex-shrink-0 animate-shimmer"></div>
            <div className="h-3.5 w-10 bg-gray-200 rounded animate-shimmer"></div>
          </div>
        </div>

        {/* Title skeleton (2 lines) */}
        <div className="space-y-2 pt-1">
          <div className="h-4.5 w-5/6 bg-gray-200 rounded animate-shimmer"></div>
          <div className="h-4.5 w-2/3 bg-gray-200 rounded animate-shimmer"></div>
        </div>

        {/* Overview skeleton (3 lines) */}
        <div className="space-y-2.5 pt-2">
          <div className="h-3 w-full bg-gray-200 rounded animate-shimmer"></div>
          <div className="h-3 w-full bg-gray-200 rounded animate-shimmer"></div>
          <div className="h-3 w-4/5 bg-gray-200 rounded animate-shimmer"></div>
        </div>

        {/* Footer separator and price/button row */}
        <div className="pt-4 border-t border-gray-100 mt-auto flex items-end justify-between">
          <div className="space-y-1.5 w-1/2">
            <div className="h-6 w-3/4 bg-gray-200 rounded animate-shimmer"></div>
            <div className="h-3 w-1/2 bg-gray-200 rounded animate-shimmer"></div>
          </div>
          <div className="h-9 w-20 bg-gray-200 rounded-lg animate-shimmer"></div>
        </div>
      </div>
    </div>
  );
};

/**
 * Full page skeleton component mimicking the Package Details details page layout.
 */
export const PackageDetailsSkeleton = () => {
  return (
    <div className="min-h-screen bg-gray-50 pb-12 pointer-events-none">
      <main className="max-w-7xl mx-auto px-0 sm:px-6 lg:px-8 mt-0 sm:mt-8 w-full">
        <div className="bg-white shadow-none sm:shadow-lg sm:rounded-2xl overflow-hidden w-full max-w-full">
          {/* Image Banner Carousel Skeleton */}
          <div className="relative w-full h-[40vh] sm:h-[60vh] animate-shimmer bg-gray-200">
            {/* Overlay indicators */}
            <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex space-x-2">
              <div className="w-2 h-2 rounded-full bg-gray-300"></div>
              <div className="w-4 h-2 rounded-full bg-gray-400"></div>
              <div className="w-2 h-2 rounded-full bg-gray-300"></div>
            </div>
          </div>

          {/* Content Wrapper Skeleton */}
          <div className="p-4 sm:p-8 w-full overflow-hidden">
            <div className="flex flex-col lg:flex-row lg:justify-between lg:items-start mb-8 gap-6 w-full">
              {/* Left Column: Title and Details */}
              <div className="flex-1 min-w-0 space-y-4">
                {/* Destination & Code Row */}
                <div className="flex items-center gap-3">
                  <div className="h-4 w-28 bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-4 w-16 bg-gray-200 rounded animate-shimmer"></div>
                </div>

                {/* Title (2 lines) */}
                <div className="space-y-3">
                  <div className="h-8 sm:h-10 w-11/12 bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-8 sm:h-10 w-2/3 bg-gray-200 rounded animate-shimmer"></div>
                </div>

                {/* Overview Paragraphs */}
                <div className="space-y-3 pt-4">
                  <div className="h-4 w-full bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-4 w-full bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-4 w-11/12 bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-4 w-5/6 bg-gray-200 rounded animate-shimmer"></div>
                </div>
              </div>

              {/* Right Column: Pricing Sidebar */}
              <div className="bg-blue-50/50 border border-blue-100 rounded-xl p-6 lg:w-80 flex-shrink-0 space-y-5">
                <div className="flex items-center space-x-2">
                  <div className="h-5 w-5 bg-blue-100 rounded-full animate-shimmer flex-shrink-0"></div>
                  <div className="h-5 w-36 bg-gray-200 rounded animate-shimmer"></div>
                </div>
                
                <div className="space-y-2 pt-2">
                  <div className="h-3 w-20 bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-10 w-48 bg-gray-200 rounded animate-shimmer"></div>
                  <div className="h-3 w-40 bg-gray-200 rounded animate-shimmer"></div>
                </div>

                <div className="h-12 bg-blue-200 rounded-lg w-full animate-shimmer mt-4"></div>
              </div>
            </div>

            {/* Inclusions & Exclusions Skeleton Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12 border-t border-gray-100 pt-8">
              {/* Inclusions */}
              <div className="bg-gray-50 rounded-xl p-6 border border-gray-100 space-y-4">
                <div className="h-6 w-40 bg-gray-200 rounded animate-shimmer mb-2"></div>
                {[1, 2, 3].map((n) => (
                  <div key={n} className="flex items-center space-x-3">
                    <div className="h-5 w-5 bg-green-100 rounded-full flex-shrink-0 animate-shimmer"></div>
                    <div className="h-4 w-5/6 bg-gray-200 rounded animate-shimmer"></div>
                  </div>
                ))}
              </div>

              {/* Exclusions */}
              <div className="bg-gray-50 rounded-xl p-6 border border-gray-100 space-y-4">
                <div className="h-6 w-40 bg-gray-200 rounded animate-shimmer mb-2"></div>
                {[1, 2, 3].map((n) => (
                  <div key={n} className="flex items-center space-x-3">
                    <div className="h-5 w-5 bg-red-100 rounded-full flex-shrink-0 animate-shimmer"></div>
                    <div className="h-4 w-5/6 bg-gray-200 rounded animate-shimmer"></div>
                  </div>
                ))}
              </div>
            </div>

            {/* Itinerary Skeleton */}
            <div className="border-t border-gray-200 pt-10">
              <div className="h-7 w-48 bg-gray-200 rounded animate-shimmer mb-8"></div>
              
              <div className="space-y-8 relative">
                {[1, 2].map((n) => (
                  <div key={n} className="relative flex flex-col md:flex-row items-start w-full">
                    {/* Timeline dot */}
                    <div className="hidden md:block absolute left-1/2 -ml-3 mt-5 h-6 w-6 rounded-full border-4 border-white bg-gray-200 shadow-sm z-10"></div>
                    
                    {/* Alternating side */}
                    {n % 2 !== 0 && <div className="hidden md:block md:w-1/2"></div>}
                    
                    {/* Content Card Skeleton */}
                    <div className="ml-10 md:ml-0 md:w-1/2 flex flex-col w-[calc(100%-2.5rem)] md:px-10">
                      <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm space-y-4 w-full">
                        <div className="flex items-center gap-2">
                          <div className="h-5 w-14 bg-blue-100 rounded animate-shimmer"></div>
                          <div className="h-5 w-48 bg-gray-200 rounded animate-shimmer"></div>
                        </div>
                        <div className="space-y-2">
                          <div className="h-3 w-full bg-gray-200 rounded animate-shimmer"></div>
                          <div className="h-3 w-full bg-gray-200 rounded animate-shimmer"></div>
                          <div className="h-3 w-2/3 bg-gray-200 rounded animate-shimmer"></div>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};
