package com.rbdip.bookstore.review;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final PurchaseHistory purchaseHistory;

    public ReviewService(ReviewRepository reviewRepository, PurchaseHistory purchaseHistory) {
        this.reviewRepository = reviewRepository;
        this.purchaseHistory = purchaseHistory;
    }

    public Review addReview(Long productId, String authorName, Integer rating, String comment) {
        purchaseHistory.hasPurchases();
        Review review = new Review(productId, authorName == null ? "anonymous" : authorName, rating, comment);
        return reviewRepository.save(review);
    }

    public List<Review> listReviews(Long productId) {
        return reviewRepository.findByProductId(productId);
    }
}
