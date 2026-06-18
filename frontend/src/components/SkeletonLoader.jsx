import React from 'react';

// Single item card skeleton for Home page
export const CardSkeleton = () => (
  <div className="flex flex-col bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden h-full">
    {/* Image area */}
    <div className="relative animate-shimmer w-full h-[200px] flex-shrink-0" />
    
    {/* Body details */}
    <div className="flex flex-col flex-1 p-5 space-y-4">
      {/* Destination + duration line */}
      <div className="flex justify-between items-center">
        <div className="h-3 w-24 rounded animate-shimmer" />
        <div className="h-3 w-16 rounded animate-shimmer" />
      </div>
      
      {/* Title */}
      <div className="space-y-2">
        <div className="h-4 w-full rounded animate-shimmer" />
        <div className="h-4 w-2/3 rounded animate-shimmer" />
      </div>
      
      {/* Short overview description lines */}
      <div className="space-y-2">
        <div className="h-2.5 w-full rounded animate-shimmer" />
        <div className="h-2.5 w-full rounded animate-shimmer" />
        <div className="h-2.5 w-5/6 rounded animate-shimmer" />
      </div>
      
      {/* Pinned footer */}
      <div className="pt-4 border-t border-gray-100 mt-auto flex justify-between items-end">
        <div className="space-y-2">
          <div className="h-5 w-28 rounded animate-shimmer" />
          <div className="h-2.5 w-16 rounded animate-shimmer" />
        </div>
        <div className="h-8 w-20 rounded-lg animate-shimmer" />
      </div>
    </div>
  </div>
);

// Entire Home page skeleton
export const HomeSkeleton = () => (
  <div className="bg-gray-50 flex-grow min-h-screen">
    <main className="max-w-7xl mx-auto py-12 px-4 sm:px-6 lg:px-8 overflow-hidden">
      {/* Header */}
      <div className="text-center mb-16 space-y-4">
        <div className="h-10 sm:h-14 w-3/4 sm:w-1/2 mx-auto rounded-xl animate-shimmer" />
        <div className="h-4 w-5/6 sm:w-1/3 mx-auto rounded animate-shimmer" />
      </div>

      {/* Tabs */}
      <div className="flex justify-center mb-10">
        <div className="h-12 w-80 rounded-xl animate-shimmer" />
      </div>

      {/* Package Card Grid */}
      <div className="grid grid-cols-1 gap-y-10 gap-x-6 sm:grid-cols-2 lg:grid-cols-3 xl:gap-x-8">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <CardSkeleton key={i} />
        ))}
      </div>
    </main>
  </div>
);

// Entire Package details page skeleton
export const PackageDetailsSkeleton = () => (
  <div className="bg-gray-50 min-h-screen py-10">
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      {/* Breadcrumb skeleton */}
      <div className="h-4 w-48 rounded mb-6 animate-shimmer" />
      
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left Column: Image and Details */}
        <div className="lg:col-span-2 space-y-8">
          {/* Main Hero Gallery Section */}
          <div className="bg-white rounded-3xl border border-gray-100 shadow-sm p-4 sm:p-6 space-y-6">
            <div className="space-y-3">
              <div className="h-8 w-3/4 rounded-xl animate-shimmer" />
              <div className="flex space-x-4">
                <div className="h-4 w-24 rounded animate-shimmer" />
                <div className="h-4 w-32 rounded animate-shimmer" />
              </div>
            </div>
            
            {/* Image Slider skeleton */}
            <div className="h-[300px] sm:h-[450px] w-full rounded-2xl animate-shimmer" />
            
            {/* Thumbnail Gallery skeleton */}
            <div className="flex space-x-3 overflow-x-auto pb-1">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-20 w-32 rounded-xl flex-shrink-0 animate-shimmer" />
              ))}
            </div>
          </div>
          
          {/* Overview Tabs section */}
          <div className="bg-white rounded-3xl border border-gray-100 shadow-sm p-6 space-y-6">
            <div className="flex space-x-6 border-b border-gray-200 pb-3">
              <div className="h-6 w-20 rounded animate-shimmer" />
              <div className="h-6 w-24 rounded animate-shimmer" />
              <div className="h-6 w-24 rounded animate-shimmer" />
            </div>
            <div className="space-y-3">
              <div className="h-4 w-full rounded animate-shimmer" />
              <div className="h-4 w-full rounded animate-shimmer" />
              <div className="h-4 w-5/6 rounded animate-shimmer" />
            </div>
          </div>
        </div>
        
        {/* Right Column: Booking Sidebar */}
        <div className="space-y-6">
          <div className="bg-white rounded-3xl border border-gray-200 shadow-lg p-6 space-y-6 sticky top-6">
            <div className="space-y-2">
              <div className="h-4 w-20 rounded animate-shimmer" />
              <div className="h-8 w-40 rounded animate-shimmer" />
            </div>
            
            <div className="h-10 w-full rounded-xl animate-shimmer" />
            
            <div className="space-y-4">
              <div className="h-12 w-full rounded-xl animate-shimmer" />
              <div className="h-12 w-full rounded-xl animate-shimmer" />
              <div className="h-12 w-full rounded-xl animate-shimmer" />
            </div>
            
            <div className="h-12 w-full rounded-xl animate-shimmer" />
          </div>
        </div>
      </div>
    </div>
  </div>
);

// Entire Image Gallery skeleton
export const GallerySkeleton = () => (
  <div className="max-w-7xl mx-auto py-10 px-4">
    {/* Gallery Header */}
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-8 space-y-4 sm:space-y-0">
      <div className="space-y-2">
        <div className="h-8 w-56 rounded-xl animate-shimmer" />
        <div className="h-4 w-72 rounded animate-shimmer" />
      </div>
      <div className="h-10 w-36 rounded-lg animate-shimmer" />
    </div>
    
    {/* Gallery Grid */}
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
      {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
        <div key={i} className="aspect-square w-full rounded-2xl shadow-sm border border-gray-100 animate-shimmer" />
      ))}
    </div>
  </div>
);

// Admin Workspace Skeleton (Sidebar + Form layout)
export const AdminSkeleton = () => (
  <div className="min-h-screen bg-gray-50">
    <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
      <div className="flex flex-col lg:flex-row gap-6">
        {/* Left Column: Form skeleton */}
        <div className="lg:w-1/2 bg-white p-6 rounded-lg shadow-sm border border-gray-200 space-y-6">
          <div className="h-7 w-40 bg-gray-200 rounded animate-shimmer" />
          
          <div className="space-y-4">
            <div className="space-y-2">
              <div className="h-3.5 w-24 bg-gray-200 rounded animate-shimmer" />
              <div className="h-10 w-full bg-gray-100 rounded-lg animate-shimmer" />
            </div>
            
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <div className="h-3.5 w-20 bg-gray-200 rounded animate-shimmer" />
                <div className="h-10 w-full bg-gray-100 rounded-lg animate-shimmer" />
              </div>
              <div className="space-y-2">
                <div className="h-3.5 w-20 bg-gray-200 rounded animate-shimmer" />
                <div className="h-10 w-full bg-gray-100 rounded-lg animate-shimmer" />
              </div>
            </div>

            <div className="space-y-2">
              <div className="h-3.5 w-16 bg-gray-200 rounded animate-shimmer" />
              <div className="h-28 w-full bg-gray-100 rounded-lg animate-shimmer" />
            </div>

            <div className="space-y-2">
              <div className="h-3.5 w-28 bg-gray-200 rounded animate-shimmer" />
              <div className="h-20 w-full bg-gray-100 rounded-lg animate-shimmer" />
            </div>

            <div className="h-12 w-full bg-gray-200 rounded-lg animate-shimmer pt-2" />
          </div>
        </div>

        {/* Right Column: Packages List skeleton */}
        <div className="lg:w-1/2 bg-white p-6 rounded-lg shadow-sm border border-gray-200 flex flex-col space-y-4">
          <div className="flex justify-between items-center pb-2 border-b border-gray-150">
            <div className="h-6 w-32 bg-gray-200 rounded animate-shimmer" />
            <div className="h-4 w-20 bg-gray-200 rounded animate-shimmer" />
          </div>
          
          <div className="h-10 w-full bg-gray-100 rounded-lg animate-shimmer" />

          {/* List items */}
          <div className="space-y-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="flex gap-4 p-3 border border-gray-150 rounded-xl bg-white shadow-sm">
                <div className="w-[100px] h-[80px] bg-gray-200 rounded-lg shrink-0 animate-shimmer" />
                <div className="flex-1 space-y-3 py-1">
                  <div className="h-4 w-3/4 bg-gray-200 rounded animate-shimmer" />
                  <div className="flex space-x-2">
                    <div className="h-3 w-16 bg-gray-200 rounded animate-shimmer" />
                    <div className="h-3 w-24 bg-gray-200 rounded animate-shimmer" />
                  </div>
                </div>
                <div className="flex flex-col justify-between items-end shrink-0 pl-2">
                  <div className="h-6 w-12 bg-gray-150 rounded-full animate-shimmer" />
                  <div className="flex space-x-2">
                    <div className="h-6 w-6 bg-gray-200 rounded-md animate-shimmer" />
                    <div className="h-6 w-6 bg-gray-200 rounded-md animate-shimmer" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  </div>
);

// Leads CRM Board Skeleton
export const LeadsSkeleton = () => (
  <div className="w-full">
    {/* Header */}
    <div className="flex items-center justify-between mb-6 border-b pb-4">
      <div className="h-8 w-40 bg-gray-205 rounded animate-shimmer" />
      <div className="h-5 w-24 bg-gray-200 rounded animate-shimmer" />
    </div>

    {/* CRM Board Columns */}
    <div className="flex gap-4 overflow-x-auto pb-6 items-start">
      {[1, 2, 3, 4].map((col) => (
        <div key={col} className="flex-shrink-0 w-80 bg-gray-50 rounded-xl border border-gray-200 flex flex-col p-3 space-y-4">
          {/* Column Header */}
          <div className="flex justify-between items-center pb-2 border-b border-gray-150">
            <div className="h-5 w-24 bg-gray-200 rounded animate-shimmer" />
            <div className="h-5 w-8 bg-gray-155 rounded-full animate-shimmer" />
          </div>

          {/* Cards */}
          <div className="space-y-3">
            {[1, 2].map((card) => (
              <div key={card} className="bg-white p-4 rounded-lg border border-gray-200 shadow-sm space-y-4">
                <div className="flex justify-between items-center border-b border-gray-50 pb-2">
                  <div className="h-3 w-14 bg-gray-100 rounded animate-shimmer" />
                  <div className="h-3 w-8 bg-gray-100 rounded animate-shimmer" />
                </div>
                
                <div className="flex justify-between items-start">
                  <div className="h-4 w-28 bg-gray-200 rounded animate-shimmer" />
                  <div className="h-3 w-16 bg-gray-150 rounded animate-shimmer" />
                </div>

                <div className="space-y-2">
                  <div className="h-3 w-full bg-gray-100 rounded animate-shimmer" />
                  <div className="h-3 w-2/3 bg-gray-100 rounded animate-shimmer" />
                </div>

                <div className="flex justify-between items-center pt-2 border-t border-gray-100">
                  <div className="h-4 w-16 bg-gray-200 rounded animate-shimmer" />
                  <div className="h-3 w-10 bg-gray-150 rounded animate-shimmer" />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  </div>
);

// User Management Skeleton
export const UsersSkeleton = () => (
  <div className="space-y-6">
    {/* Header */}
    <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b pb-4">
      <div>
        <div className="h-7 w-60 bg-gray-200 rounded animate-shimmer mb-2" />
        <div className="h-4 w-80 bg-gray-150 rounded animate-shimmer" />
      </div>
      <div className="h-10 w-full sm:w-72 bg-gray-200 rounded-md animate-shimmer" />
    </div>

    {/* Summary Stats Cards */}
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
      {[1, 2, 3].map((card) => (
        <div key={card} className="bg-white border border-gray-200 rounded-lg p-4 flex items-center gap-3">
          <div className="w-10 h-10 bg-gray-200 rounded-md shrink-0 animate-shimmer" />
          <div className="flex-1 space-y-2">
            <div className="h-3 w-24 bg-gray-150 rounded animate-shimmer" />
            <div className="h-6 w-12 bg-gray-200 rounded animate-shimmer" />
          </div>
        </div>
      ))}
    </div>

    {/* Table Skeleton */}
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
      <div className="min-w-full divide-y divide-gray-200">
        {/* Table Headers */}
        <div className="bg-gray-50 px-6 py-3 flex justify-between">
          <div className="h-3.5 w-20 bg-gray-200 rounded animate-shimmer" />
          <div className="h-3.5 w-20 bg-gray-200 rounded animate-shimmer" />
          <div className="h-3.5 w-20 bg-gray-200 rounded animate-shimmer" />
          <div className="h-3.5 w-20 bg-gray-200 rounded animate-shimmer" />
          <div className="h-3.5 w-24 bg-gray-200 rounded animate-shimmer" />
        </div>

        {/* Table Rows */}
        <div className="divide-y divide-gray-200">
          {[1, 2, 3, 4, 5].map((row) => (
            <div key={row} className="px-6 py-4 flex items-center justify-between gap-4">
              {/* User info */}
              <div className="flex items-center gap-3 w-1/4">
                <div className="w-10 h-10 rounded-full bg-gray-200 shrink-0 animate-shimmer" />
                <div className="flex-1 space-y-2">
                  <div className="h-4 w-28 bg-gray-200 rounded animate-shimmer" />
                  <div className="h-3 w-24 bg-gray-150 rounded animate-shimmer" />
                </div>
              </div>

              {/* Contact info */}
              <div className="w-1/4 space-y-2">
                <div className="h-3.5 w-40 bg-gray-200 rounded animate-shimmer" />
                <div className="h-3.5 w-28 bg-gray-150 rounded animate-shimmer" />
              </div>

              {/* Provider */}
              <div className="w-1/6">
                <div className="h-5 w-16 bg-gray-200 rounded-full animate-shimmer" />
              </div>

              {/* Role */}
              <div className="w-1/6">
                <div className="h-5 w-16 bg-gray-200 rounded-full animate-shimmer" />
              </div>

              {/* Dropdown action */}
              <div className="w-1/6">
                <div className="h-9 w-32 bg-gray-200 rounded-md animate-shimmer" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  </div>
);

