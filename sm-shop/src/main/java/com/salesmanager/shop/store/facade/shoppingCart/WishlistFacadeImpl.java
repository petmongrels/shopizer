package com.salesmanager.shop.store.facade.shoppingCart;

import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.shop.mapper.cart.ReadableShoppingCartMapper;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.shoppingCart.facade.v1.WishlistFacade;

@Service("wishlistFacade")
public class WishlistFacadeImpl implements WishlistFacade {

	@Autowired
	private CustomerService customerService;

	@Autowired
	private ShoppingCartService shoppingCartService;

	@Autowired
	private ReadableShoppingCartMapper readableShoppingCartMapper;

	@Override
	public ReadableShoppingCart getWishlist(Long customerId, MerchantStore store, Language language) {
		Validate.notNull(customerId, "Customer id cannot be null");
		Validate.notNull(store, "MerchantStore cannot be null");

		try {
			Customer customer = customerService.getById(customerId);
			if (customer == null) {
				throw new ResourceNotFoundException("No Customer found for id [" + customerId + "]");
			}

			ShoppingCart wishlist = shoppingCartService.getWishlist(customer, store);
			if (wishlist == null) {
				return null;
			}

			return readableShoppingCartMapper.convert(wishlist, store, language);
		} catch (ResourceNotFoundException e) {
			throw e;
		} catch (Exception e) {
			throw new ServiceRuntimeException("Error getting wishlist for customer [" + customerId + "]", e);
		}
	}

	@Override
	public ReadableShoppingCart saveForLater(Long customerId, String sku, MerchantStore store, Language language) {
		Validate.notNull(customerId, "Customer id cannot be null");
		Validate.notNull(sku, "SKU cannot be null");
		Validate.notNull(store, "MerchantStore cannot be null");

		try {
			Customer customer = customerService.getById(customerId);
			if (customer == null) {
				throw new ResourceNotFoundException("No Customer found for id [" + customerId + "]");
			}

			// Find the cart item by SKU in the customer's active cart
			ShoppingCart cart = shoppingCartService.getShoppingCart(customer, store);
			if (cart == null) {
				throw new ResourceNotFoundException("No active cart found for customer [" + customerId + "]");
			}

			ShoppingCartItem targetItem = findItemBySku(cart.getLineItems(), sku);
			if (targetItem == null) {
				throw new ResourceNotFoundException("Product with sku [" + sku + "] not found in cart");
			}

			shoppingCartService.moveToWishlist(targetItem.getId(), customer, store);

			// Return updated cart
			ShoppingCart updatedCart = shoppingCartService.getShoppingCart(customer, store);
			if (updatedCart == null) {
				return new ReadableShoppingCart();
			}

			return readableShoppingCartMapper.convert(updatedCart, store, language);
		} catch (ResourceNotFoundException e) {
			throw e;
		} catch (Exception e) {
			throw new ServiceRuntimeException("Error saving item for later", e);
		}
	}

	@Override
	public ReadableShoppingCart moveToCart(Long customerId, String sku, MerchantStore store, Language language) {
		Validate.notNull(customerId, "Customer id cannot be null");
		Validate.notNull(sku, "SKU cannot be null");
		Validate.notNull(store, "MerchantStore cannot be null");

		try {
			Customer customer = customerService.getById(customerId);
			if (customer == null) {
				throw new ResourceNotFoundException("No Customer found for id [" + customerId + "]");
			}

			// Find the wishlist item by SKU
			ShoppingCart wishlist = shoppingCartService.getWishlist(customer, store);
			if (wishlist == null) {
				throw new ResourceNotFoundException("No wishlist found for customer [" + customerId + "]");
			}

			ShoppingCartItem targetItem = findItemBySku(wishlist.getLineItems(), sku);
			if (targetItem == null) {
				throw new ResourceNotFoundException("Product with sku [" + sku + "] not found in wishlist");
			}

			shoppingCartService.moveToCart(targetItem.getId(), customer, store);

			// Return updated cart
			ShoppingCart updatedCart = shoppingCartService.getShoppingCart(customer, store);
			if (updatedCart == null) {
				return new ReadableShoppingCart();
			}

			return readableShoppingCartMapper.convert(updatedCart, store, language);
		} catch (ResourceNotFoundException e) {
			throw e;
		} catch (Exception e) {
			throw new ServiceRuntimeException("Error moving item to cart", e);
		}
	}

	@Override
	public void removeFromWishlist(Long customerId, String sku, MerchantStore store, Language language) {
		Validate.notNull(customerId, "Customer id cannot be null");
		Validate.notNull(sku, "SKU cannot be null");
		Validate.notNull(store, "MerchantStore cannot be null");

		try {
			Customer customer = customerService.getById(customerId);
			if (customer == null) {
				throw new ResourceNotFoundException("No Customer found for id [" + customerId + "]");
			}

			ShoppingCart wishlist = shoppingCartService.getWishlist(customer, store);
			if (wishlist == null) {
				throw new ResourceNotFoundException("No wishlist found for customer [" + customerId + "]");
			}

			ShoppingCartItem targetItem = findItemBySku(wishlist.getLineItems(), sku);
			if (targetItem == null) {
				throw new ResourceNotFoundException("Product with sku [" + sku + "] not found in wishlist");
			}

			shoppingCartService.deleteShoppingCartItem(targetItem.getId());
		} catch (ResourceNotFoundException e) {
			throw e;
		} catch (Exception e) {
			throw new ServiceRuntimeException("Error removing item from wishlist", e);
		}
	}

	private ShoppingCartItem findItemBySku(Set<ShoppingCartItem> items, String sku) {
		if (items == null) {
			return null;
		}
		return items.stream()
				.filter(item -> sku.equals(item.getSku()))
				.findFirst()
				.orElse(null);
	}
}
