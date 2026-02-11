package com.salesmanager.shop.store.api.v1.shoppingCart;

import java.security.Principal;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.customer.facade.v1.CustomerFacade;
import com.salesmanager.shop.store.controller.shoppingCart.facade.v1.WishlistFacade;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import springfox.documentation.annotations.ApiIgnore;

@Controller
@RequestMapping("/api/v1")
@Api(tags = { "Wishlist / Save for later api" })
@SwaggerDefinition(tags = {
		@Tag(name = "Wishlist resource", description = "Save for later, move to cart and manage wishlist items") })
public class WishlistApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(WishlistApi.class);

	@Inject
	private WishlistFacade wishlistFacade;

	@Autowired
	private CustomerFacade customerFacadev1;

	@Autowired
	private com.salesmanager.shop.store.controller.customer.facade.CustomerFacade customerFacade;

	@ResponseStatus(HttpStatus.OK)
	@GetMapping(value = "/auth/customer/wishlist")
	@ApiOperation(httpMethod = "GET", value = "Get customer's wishlist", produces = "application/json", response = ReadableShoppingCart.class)
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en") })
	public @ResponseBody ReadableShoppingCart getWishlist(
			@ApiIgnore MerchantStore merchantStore,
			@ApiIgnore Language language,
			HttpServletRequest request) {

		Customer customer = getAuthenticatedCustomer(request, merchantStore);

		ReadableShoppingCart wishlist = wishlistFacade.getWishlist(customer.getId(), merchantStore, language);
		if (wishlist == null) {
			throw new ResourceNotFoundException("No wishlist found for customer [" + customer.getId() + "]");
		}

		return wishlist;
	}

	@ResponseStatus(HttpStatus.OK)
	@PostMapping(value = "/auth/customer/wishlist/product/{sku}")
	@ApiOperation(httpMethod = "POST", value = "Move item from cart to wishlist (save for later)", produces = "application/json", response = ReadableShoppingCart.class)
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en") })
	public @ResponseBody ReadableShoppingCart saveForLater(
			@PathVariable("sku") String sku,
			@ApiIgnore MerchantStore merchantStore,
			@ApiIgnore Language language,
			HttpServletRequest request) {

		Customer customer = getAuthenticatedCustomer(request, merchantStore);
		return wishlistFacade.saveForLater(customer.getId(), sku, merchantStore, language);
	}

	@ResponseStatus(HttpStatus.OK)
	@PostMapping(value = "/auth/customer/cart/wishlist/{sku}")
	@ApiOperation(httpMethod = "POST", value = "Move item from wishlist to cart", produces = "application/json", response = ReadableShoppingCart.class)
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en") })
	public @ResponseBody ReadableShoppingCart moveToCart(
			@PathVariable("sku") String sku,
			@ApiIgnore MerchantStore merchantStore,
			@ApiIgnore Language language,
			HttpServletRequest request) {

		Customer customer = getAuthenticatedCustomer(request, merchantStore);
		return wishlistFacade.moveToCart(customer.getId(), sku, merchantStore, language);
	}

	@ResponseStatus(HttpStatus.NO_CONTENT)
	@DeleteMapping(value = "/auth/customer/wishlist/product/{sku}")
	@ApiOperation(httpMethod = "DELETE", value = "Remove item from wishlist", produces = "application/json")
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en") })
	public ResponseEntity<Void> removeFromWishlist(
			@PathVariable("sku") String sku,
			@ApiIgnore MerchantStore merchantStore,
			@ApiIgnore Language language,
			HttpServletRequest request) {

		Customer customer = getAuthenticatedCustomer(request, merchantStore);
		wishlistFacade.removeFromWishlist(customer.getId(), sku, merchantStore, language);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	private Customer getAuthenticatedCustomer(HttpServletRequest request, MerchantStore store) {
		Principal principal = request.getUserPrincipal();
		Customer customer;
		try {
			customer = customerFacade.getCustomerByUserName(principal.getName(), store);
		} catch (Exception e) {
			throw new ServiceRuntimeException("Exception while getting customer [" + principal.getName() + "]");
		}

		if (customer == null) {
			throw new ResourceNotFoundException("No Customer found for principal [" + principal.getName() + "]");
		}

		customerFacadev1.authorize(customer, principal);
		return customer;
	}
}
