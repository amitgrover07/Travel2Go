import { useEffect } from 'react';

const SEO = ({ title, description, keywords }) => {
  useEffect(() => {
    // Update title
    document.title = title ? `${title} | travel2go.in` : 'travel2go.in | Premium Holiday Packages';

    // Update meta description
    const metaDescription = document.querySelector('meta[name="description"]');
    if (metaDescription) {
      metaDescription.setAttribute('content', description || 'Discover breathtaking holiday packages with travel2go.in.');
    }

    // Update meta keywords
    const metaKeywords = document.querySelector('meta[name="keywords"]');
    if (metaKeywords) {
      metaKeywords.setAttribute('content', keywords || 'travel, holiday, packages, travel2go.in');
    }
  }, [title, description, keywords]);

  return null;
};

export default SEO;
