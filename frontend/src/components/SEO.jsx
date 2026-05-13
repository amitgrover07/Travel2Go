import { useEffect } from 'react';

const SEO = ({ title, description, keywords }) => {
  useEffect(() => {
    // Update title
    document.title = title ? `${title} | Travel2Go` : 'Travel2Go | Premium Holiday Packages';

    // Update meta description
    const metaDescription = document.querySelector('meta[name="description"]');
    if (metaDescription) {
      metaDescription.setAttribute('content', description || 'Discover breathtaking holiday packages with Travel2Go.');
    }

    // Update meta keywords
    const metaKeywords = document.querySelector('meta[name="keywords"]');
    if (metaKeywords) {
      metaKeywords.setAttribute('content', keywords || 'travel, holiday, packages, travel2go');
    }
  }, [title, description, keywords]);

  return null;
};

export default SEO;
