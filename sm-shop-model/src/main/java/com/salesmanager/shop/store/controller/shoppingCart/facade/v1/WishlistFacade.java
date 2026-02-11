package com.salesmanager.shop.store.controller.shoppingCart.facade.v1;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;

public interface WishlistFacade {

	ReadableShoppingCart getWishlist(Long customerId, MerchantStore store, Language language);

	ReadableShoppingCart saveForLater(Long customerId, String sku, MerchantStore store, Language language);

	ReadableShoppingCart moveToCart(Long customerId, String sku, MerchantStore store, Language language);

	void removeFromWishlist(Long customerId, String sku, MerchantStore store, Language language);

}
