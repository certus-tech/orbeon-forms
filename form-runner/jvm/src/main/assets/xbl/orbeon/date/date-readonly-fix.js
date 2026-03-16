/**
 * CERTUS script for handling read-only fields for Date as
 * Orbeon 2017.X does not support this.
 * JavaScript state monitoring for date field readonly state transitions.
 * This ensures that when XForms readonly state changes, the HTML input
 * is properly updated to reflect the new state.
 *
 * This addresses the issue where xh:input doesn't automatically react
 * to XForms state changes in the 2017.2 version.
 */

(function() {
    'use strict';

    // Store initialized controls to avoid duplicate listeners
    var initializedControls = {};
    var verificationInterval = null;
    var bodyObserver = null;

    /**
     * Force remove all readonly-related attributes from input
     */
    function forceInputEditable(input) {
        if (!input) return false;

        try {
            input.readOnly = false;
            if (input.hasAttribute('readonly')) {
                input.removeAttribute('readonly');
            }
            input.style.pointerEvents = 'auto';
            if (input.hasAttribute('disabled')) {
                input.removeAttribute('disabled');
            }
            input.disabled = false;
            return true;
        } catch (e) {
            if (window.console) console.error('[DateField] forceInputEditable error:', e);
            return false;
        }
    }

    /**
     * Force set readonly state on input
     */
    function forceInputReadonly(input) {
        if (!input) return false;

        try {
            input.readOnly = true;
            input.setAttribute('readonly', 'readonly');
            input.style.pointerEvents = 'none';
            return true;
        } catch (e) {
            if (window.console) console.error('[DateField] forceInputReadonly error:', e);
            return false;
        }
    }

    /**
     * Update the HTML input's readonly state based on the component's class
     */
    function updateInputReadonlyState(container) {
        if (!container) return false;

        try {
            // Date field inputs are direct children, not in a wrapper
            var input = container.querySelector('input[type="text"]');
            if (!input) return false;

            var isReadonly = container.classList.contains('xforms-readonly');

            if (isReadonly) {
                if (!input.hasAttribute('data-original-tabindex')) {
                    input.setAttribute('data-original-tabindex', input.getAttribute('tabindex') || '');
                }
                input.removeAttribute('tabindex');
                return forceInputReadonly(input);
            } else {
                var originalTabindex = input.getAttribute('data-original-tabindex');
                if (originalTabindex !== null && originalTabindex !== '') {
                    input.setAttribute('tabindex', originalTabindex);
                }
                input.removeAttribute('data-original-tabindex');
                return forceInputEditable(input);
            }
        } catch (e) {
            if (window.console) console.error('[DateField] updateInputReadonlyState error:', e);
            return false;
        }
    }

    /**
     * Initialize monitoring for a date field component
     */
    function initializeControl(container) {
        if (!container) return false;
        if (typeof container.id !== 'string' || !container.id) return false;
        if (initializedControls[container.id]) return true;

        try {
            updateInputReadonlyState(container);

            var observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                        setTimeout(function() {
                            updateInputReadonlyState(container);
                        }, 10);
                    }
                });
            });

            observer.observe(container, {
                attributes: true,
                attributeFilter: ['class']
            });

            initializedControls[container.id] = {
                observer: observer,
                container: container
            };

            return true;
        } catch (e) {
            if (window.console) console.error('[DateField] initializeControl error:', e);
            return false;
        }
    }

    /**
     * Find and initialize all date field controls on the page
     */
    function initializeAllControls() {
        try {
            // Date fields have class 'input-append date'
            var dateFields = document.querySelectorAll('.input-append.date');
            if (dateFields.length === 0) return;

            dateFields.forEach(function(fieldDiv) {
                // Get the parent container which has the xforms classes
                var container = fieldDiv.closest('.xbl-fr-date');
                if (container) {
                    initializeControl(container);
                }
            });
        } catch (e) {
            if (window.console) console.error('[DateField] initializeAllControls error:', e);
        }
    }

    /**
     * Clean up observers for controls that have been removed from DOM
     */
    function cleanupRemovedControls() {
        try {
            Object.keys(initializedControls).forEach(function(controlId) {
                var control = initializedControls[controlId];
                if (control && control.container && !document.body.contains(control.container)) {
                    if (control.observer) {
                        control.observer.disconnect();
                    }
                    delete initializedControls[controlId];
                }
            });
        } catch (e) {
            if (window.console) console.error('[DateField] cleanupRemovedControls error:', e);
        }
    }

    /**
     * Periodic state verification - ensures fields stay in sync
     */
    function verifyAllControls() {
        try {
            Object.keys(initializedControls).forEach(function(controlId) {
                var control = initializedControls[controlId];
                if (!control || !control.container) return;

                var input = control.container.querySelector('input[type="text"]');
                if (!input) return;

                var isReadonly = control.container.classList.contains('xforms-readonly');
                var inputIsReadonly = input.readOnly || input.disabled || input.hasAttribute('readonly');

                if (isReadonly !== inputIsReadonly) {
                    updateInputReadonlyState(control.container);
                }
            });
        } catch (e) {
            if (window.console) console.error('[DateField] verifyAllControls error:', e);
        }
    }

    /**
     * Start the monitoring system
     */
    function startMonitoring() {
        initializeAllControls();

        if (!bodyObserver && document.body) {
            bodyObserver = new MutationObserver(function() {
                initializeAllControls();
            });
            bodyObserver.observe(document.body, {
                childList: true,
                subtree: true
            });
        }

        if (!verificationInterval) {
            verificationInterval = setInterval(function() {
                cleanupRemovedControls();
                verifyAllControls();
                // run every 5 seconds
            }, 5000); 
        }
    }

    /**
     * Stop the monitoring system
     */
    function stopMonitoring() {
        if (verificationInterval) {
            clearInterval(verificationInterval);
            verificationInterval = null;
        }
        if (bodyObserver) {
            bodyObserver.disconnect();
            bodyObserver = null;
        }
        cleanupRemovedControls();
    }

    // Create the export object
    var DateFieldReadOnlyMonitor = {
        updateState: updateInputReadonlyState,
        initialize: initializeControl,
        initializeAll: initializeAllControls,
        forceEditable: forceInputEditable,
        forceReadonly: forceInputReadonly,
        start: startMonitoring,
        stop: stopMonitoring
    };

    // Export to ORBEON namespace if it exists, otherwise use window
    if (typeof ORBEON !== 'undefined' && ORBEON.xforms && ORBEON.xforms.XBL) {
        ORBEON.xforms.XBL.DateFieldReadOnlyMonitor = DateFieldReadOnlyMonitor;
    } else {
        // Export to window as fallback
        window.DateFieldReadOnlyMonitor = DateFieldReadOnlyMonitor;
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startMonitoring);
    } else {
        if (document.body) {
            startMonitoring();
        } else {
            document.addEventListener('DOMContentLoaded', startMonitoring);
        }
    }

})();