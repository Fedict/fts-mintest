var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var NameForm = function (_React$Component) {
    _inherits(NameForm, _React$Component);

    function NameForm(props) {
        var _this$state;

        _classCallCheck(this, NameForm);

        var _this = _possibleConstructorReturn(this, (NameForm.__proto__ || Object.getPrototypeOf(NameForm)).call(this, props));

        _this.getTestFileNames();

        _this.policyDigestAlgorithms = ["SHA1", "SHA224", "SHA256", "SHA384", "SHA512", "SHA3_224", "SHA3_256", "SHA3_384", "SHA3_512", "SHAKE128", "SHAKE256", "SHAKE256_512", "RIPEMD160", "MD2", "MD5", "WHIRLPOOL", "Dummy for out of range test"];

        _this.profilePerType = {
            pdf: ["PADES_1", "PADES_LTA", "PADES_LTA_EXP_ALLOW"],
            xml: ["XADES_1", "XADES_2", "XADES_LT", "XADES_LTA"],
            bin: ["CADES_1", "CADES_2", "CADES_LTA", "CADES_LTA_ENVELOPING"]
        };

        _this.state = (_this$state = {
            signType: 'Legacy',
            names: [],
            xslt: '',
            out: 'out.pdf',
            outPathPrefix: 'signed_',
            profiles: [],
            psp: '',
            psfN: 'signature_1',
            psfC: '1,30,20,180,60',
            psfP: false,
            lang: 'en',
            noDownload: false,
            signTimeout: '',
            allowedToSign: '',
            policyId: ''
        }, _defineProperty(_this$state, "policyId", ''), _defineProperty(_this$state, "policyDescription", 'Policy Description'), _defineProperty(_this$state, "policyDigestAlgorithm", 'SHA512'), _defineProperty(_this$state, "requestDocumentReadConfirm", false), _defineProperty(_this$state, "previewDocuments", true), _defineProperty(_this$state, "selectDocuments", false), _defineProperty(_this$state, "profilesForInputs", _this.profilePerType.pdf), _defineProperty(_this$state, "inputFiles", []), _defineProperty(_this$state, "inputFileExts", []), _defineProperty(_this$state, "pspFiles", []), _defineProperty(_this$state, "xsltFiles", []), _defineProperty(_this$state, "reasonForNoSubmit", null), _this$state);

        _this.handleSubmit = _this.handleSubmit.bind(_this);
        _this.handleChange = _this.handleChange.bind(_this);
        return _this;
    }

    //--------------------------------------------------------------------------

    _createClass(NameForm, [{
        key: "hasFileExts",
        value: function hasFileExts(exts, all) {
            return this.state.inputFileExts.find(function (ext) {
                index = exts.indexOf(ext);
                if (index >= 0) {
                    if (!all || exts.length == 1) return true;
                    exts.splice(index, 1);
                }
                return false;
            });
        }

        //--------------------------------------------------------------------------

    }, {
        key: "handleChange",
        value: function handleChange(event) {
            target = event.target;
            value = target.value;
            if (target.type === 'checkbox') value = target.checked;else if (target.type === 'select-multiple') {
                value = Array.from(target.selectedOptions, function (item) {
                    return item.value;
                });
            }
            this.setState(_defineProperty({}, target.id, value), this.adaptToChanges(target.id, value));
        }

        //--------------------------------------------------------------------------

    }, {
        key: "checkProfile",
        value: function checkProfile(fileType, profiles) {
            if (!this.hasFileExts([fileType])) return null;
            if (this.profilePerType[fileType].filter(function (n) {
                return profiles.indexOf(n) !== -1;
            }).length !== 0) return null;

            return 'A file of type "' + fileType + '" was selected but no Profile matching it was selected';
        }

        //--------------------------------------------------------------------------

    }, {
        key: "adaptToChanges",
        value: function adaptToChanges(targetId, value) {
            var _this2 = this;

            names = targetId === 'names' ? value : this.state.names;
            signType = targetId === 'signType' ? value : this.state.signType;
            profiles = targetId === 'profiles' ? value : this.state.profiles;
            psfN = targetId === 'psfN' ? value : this.state.psfN;
            out = targetId === 'out' ? value : this.state.out;

            if (signType == 'Legacy' && value.length != 1) {
                if (targetId === 'names') signType = 'Standard';else if (targetId === 'signType') names = [names[0]];
            }

            this.state.inputFileExts = [];
            names.forEach(function (name) {
                ext = name.split(".").pop().toLowerCase();
                if (_this2.state.inputFileExts.indexOf(ext) < 0) _this2.state.inputFileExts.push(ext);
            });

            if (signType !== 'XadesMultiFile') {
                profilesForInputs = [];
                this.state.inputFileExts.forEach(function (ext) {
                    profilesForInputs = profilesForInputs.concat(_this2.profilePerType[ext]);
                });

                if (profilesForInputs.toString() !== this.state.profilesForInputs.toString()) profiles = [profilesForInputs[0]];
                maxProfiles = names.length === 1 ? 1 : 2;
                if (profiles.length > maxProfiles) {
                    profiles.length = maxProfiles;
                }
            } else profiles = profilesForInputs = ['MDOC_XADES_LTA'];

            if (signType !== 'Standard' || names.length === 1) {
                outFileExt = signType === 'XadesMultiFile' ? "xml" : !names[0] ? "pdf" : names[0].split(".").pop().toLowerCase();

                if (out.indexOf('.') >= 0) {
                    bitsOut = out.split('.');
                    bitsOut.pop();
                    bitsOut.push(outFileExt);
                } else if (out === '') out = 'out';

                out = bitsOut.join(".");
            } else out = '';

            if (targetId === 'names') {
                acroName = this.extractAcroformName(names[0]);
                if (acroName !== '') psfN = acroName;
            }

            reasonForNoSubmit = null;
            if (!reasonForNoSubmit && (names.length === 1 || signType === 'XadesMultiFile') && out.length < 5) {
                reasonForNoSubmit = "The output file name must be > 5 character";
            }

            if (signType === 'Legacy') {
                if (!reasonForNoSubmit && this.hasFileExts(['pdf'])) {
                    psfC = targetId === 'psfC' ? value : this.state.psfC;
                    if (psfC) {
                        if (!/^[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+$/.test(psfC)) {
                            reasonForNoSubmit = "the 'PDF signature field coordinates' (PDF page#,x,y,width,height) must look like '3,10,10,30,30'";
                        }
                    } else {
                        if (!psfN) {
                            reasonForNoSubmit = "you must provide either a 'PDF signature field name' or 'PDF signature field coordinates'";
                        }
                    }
                    if (reasonForNoSubmit) reasonForNoSubmit = "For PDF input file " + reasonForNoSubmit;
                }
            }
            if (signType === 'Standard') {
                reasonForNoSubmit = this.checkProfile('pdf', profiles);
                if (reasonForNoSubmit == null) reasonForNoSubmit = this.checkProfile('xml', profiles);
                if (reasonForNoSubmit == null) {
                    outPathPrefix = targetId === 'outPathPrefix' ? value : this.state.outPathPrefix;
                    if (names.length > 1 && outPathPrefix === '') reasonForNoSubmit = 'Output prefix can\'t be empty for \'Standard\' multifile';
                }
            }

            this.setState({
                names: names,
                signType: signType,
                profilesForInputs: profilesForInputs,
                profiles: profiles,
                out: out,
                psfN: psfN,
                reasonForNoSubmit: reasonForNoSubmit
            });
        }

        //--------------------------------------------------------------------------

    }, {
        key: "extractAcroformName",
        value: function extractAcroformName(inFileName) {
            if (inFileName) {
                start = inFileName.indexOf('~');
                if (start >= 0) {
                    end = inFileName.indexOf('.', ++start);
                    if (end >= 0) return inFileName.substring(start, end);
                }
            }
            return '';
        }

        //--------------------------------------------------------------------------

    }, {
        key: "getPdfParams",
        value: function getPdfParams(src, dst, legacy) {
            if (src.psp) dst[legacy ? 'psp' : 'pspFilePath'] = src.psp;
            if (src.lang) dst[legacy ? 'lang' : 'signLanguage'] = src.lang;
            if (src.psfN) dst.psfN = src.psfN;
            if (src.psfP) dst.psfP = src.psfP;
            if (src.psfC) dst.psfC = src.psfC.replaceAll(",", "%2C");
        }

        //--------------------------------------------------------------------------

    }, {
        key: "handleSubmit",
        value: function handleSubmit(event) {
            cleanState = Object.assign({}, this.state);

            if (!cleanState.allowedToSign) delete cleanState.allowedToSign;
            if (!cleanState.signTimeout) delete cleanState.signTimeout;

            if (cleanState.signType === 'Legacy') {
                urlParams = {
                    in: names[0],
                    out: cleanState.out,
                    prof: cleanState.profiles[0],
                    signTimeout: cleanState.signTimeout,
                    noDownload: cleanState.noDownload,
                    requestDocumentReadConfirm: cleanState.requestDocumentReadConfirm
                };
                if (cleanState.allowedToSign) {
                    urlParams.allowedToSign = cleanState.allowedToSign.split(",").map(function (natNum) {
                        return { nn: natNum };
                    });
                }
                if (cleanState.xslt) urlParams.xslt = cleanState.xslt;

                extension = names[0].split('.').pop().toLowerCase();
                if (extension == "pdf") {
                    this.getPdfParams(cleanState, urlParams, true);
                } else if (extension == "xml") {
                    if (cleanState.xslt) urlParams.xslt = cleanState.xslt;
                    if (cleanState.policyId) {
                        urlParams.policyId = cleanState.policyId.replaceAll(":", "%3A");
                        urlParams.policyDescription = cleanState.policyDescription;
                        urlParams.policyDigestAlgorithm = cleanState.policyDigestAlgorithm;
                    }
                }
            } else {
                urlParams = {
                    signType: cleanState.signType,
                    signTimeout: cleanState.signTimeout,
                    requestDocumentReadConfirm: cleanState.requestDocumentReadConfirm,
                    selectDocuments: cleanState.selectDocuments,
                    outDownload: cleanState.noDownload === false,
                    signProfile: cleanState.profiles[0],
                    altSignProfile: cleanState.profiles[1]
                };
                if (cleanState.allowedToSign) urlParams.nnAllowedToSign = cleanState.allowedToSign.split(",");

                if (cleanState.previewDocuments && cleanState.signType === 'Standard' && cleanState.names.length !== 1) {
                    urlParams.previewDocuments = cleanState.previewDocuments;
                }
                if (cleanState.signType === 'XadesMultiFile' || cleanState.names.length === 1) {
                    urlParams.outFilePath = cleanState.out;
                } else {
                    urlParams.outPathPrefix = cleanState.outPathPrefix;
                }
                if (cleanState.signType === 'XadesMultiFile' && cleanState.xslt) urlParams.outXsltPath = cleanState.xslt;

                if (cleanState.policyId) {
                    urlParams.policy = { id: cleanState.policyId.replaceAll(":", "%3A"), description: cleanState.policyDescription, digestAlgorithm: cleanState.policyDigestAlgorithm };
                }

                count = 0;
                urlParams.inputs = cleanState.names.map(function (name) {
                    return cleanState.signType === 'XadesMultiFile' ? { filePath: name, xmlEltId: 'ID' + count++ } : { filePath: name };
                });

                if (cleanState.names.length === 1) {
                    if (cleanState.names[0].endsWith("pdf")) {
                        this.getPdfParams(cleanState, urlParams.inputs[0]);
                    } else if (cleanState.names[0].endsWith("xml")) {
                        if (cleanState.xslt) urlParams.inputs[0].displayXsltPath = cleanState.xslt;
                    }
                }
            }

            url = JSON.stringify(urlParams);
            url = url.replaceAll("{", "@o").replaceAll("}", "@c").replaceAll("[", "@O").replaceAll("]", "@C").replaceAll(",", "@S").replaceAll('"', "'").replaceAll(":", "@s");
            window.location = "sign?json=" + url;
            event.preventDefault();
        }

        //--------------------------------------------------------------------------

    }, {
        key: "getTestFileNames",
        value: function getTestFileNames() {
            var _this3 = this;

            fetch("/getFileList").then(function (response) {
                return response.text();
            }).then(function (response) {
                pspFiles = [''];
                xsltFiles = [''];
                inputFiles = [];

                var fileNames = response.split(",").sort();
                fileNames.forEach(function (fileName) {
                    pos = fileName.lastIndexOf('.');
                    if (pos >= 0) {
                        ext = fileName.substring(pos + 1).toLowerCase();
                        if (ext == "pdf" || ext == "xml") inputFiles.push(fileName);else if (ext == "psp") pspFiles.push(fileName);else if (ext == "xslt") xsltFiles.push(fileName);
                    }
                });

                selectedFilename = inputFiles[0];
                ext = selectedFilename.split(".").pop().toLowerCase();
                inputFileExts = [ext];
                bitsOut = _this3.state.out.toLowerCase().split(".");
                bitsOut[bitsOut.length - 1] = ext;
                out = bitsOut.join("."), _this3.setState({
                    pspFiles: pspFiles,
                    psp: pspFiles[0],
                    xsltFiles: xsltFiles,
                    xslt: xsltFiles[0],
                    inputFiles: inputFiles,
                    names: [selectedFilename],
                    profilesForInputType: _this3.profilePerType[ext],
                    profiles: [_this3.profilePerType[ext][0]],
                    out: out,
                    psfN: _this3.extractAcroformName(_this3.state.signType, [selectedFilename])
                });
            });
        }
    }, {
        key: "render",
        value: function render() {

            singlePdf = false;
            singleXML = false;
            names = this.state.names;
            if (names && names.length == 1) {
                name = names[0].toLowerCase();
                singlePdf = name.endsWith('.pdf');
                singleXML = name.endsWith('.xml');
            }
            var hasPolicy = singleXML && this.state.policyId;
            return React.createElement(
                "form",
                { onSubmit: this.handleSubmit },
                React.createElement(
                    "table",
                    { style: { border: '1px solid', width: '600px' } },
                    React.createElement(
                        "tbody",
                        null,
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement(
                                    "b",
                                    null,
                                    "General parameters"
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Signing type :"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "signType", value: this.state.signType, onChange: this.handleChange },
                                    React.createElement(
                                        "option",
                                        null,
                                        "Legacy"
                                    ),
                                    React.createElement(
                                        "option",
                                        null,
                                        "Standard"
                                    ),
                                    React.createElement(
                                        "option",
                                        null,
                                        "XadesMultiFile"
                                    )
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Input file name :"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { style: { height: '200px' }, id: "names", multiple: true, value: this.state.names, onChange: this.handleChange },
                                    this.state.inputFiles.map(function (inputFile) {
                                        return React.createElement(
                                            "option",
                                            { key: inputFile },
                                            inputFile
                                        );
                                    })
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Output file name:"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "out", type: "text", value: this.state.out, onChange: this.handleChange, disabled: this.state.signType === 'Standard' && this.state.names.length > 1 })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Output prefix:"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "outPathPrefix", type: "text", value: this.state.outPathPrefix, onChange: this.handleChange, disabled: this.state.signType !== 'Standard' })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Signing profile :"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "profiles", value: this.state.profiles, onChange: this.handleChange, multiple: true },
                                    this.state.profilesForInputs.map(function (profile) {
                                        return React.createElement(
                                            "option",
                                            { key: profile },
                                            profile
                                        );
                                    })
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "NN Allowed to Sign (Comma separated): "
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "allowedToSign", type: "text", value: this.state.allowedToSign, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Sign timeout (in seconds)"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "signTimeout", type: "text", value: this.state.signTimeout, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Disable output file download"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "noDownload", type: "checkbox", value: this.state.noDownload, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Request read confirmation"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "requestDocumentReadConfirm", type: "checkbox", value: this.state.requestDocumentReadConfirm, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Preview documents"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "previewDocuments", disabled: this.state.signType !== 'XadesMultiFile', type: "checkbox", value: this.state.previewDocuments, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Select documents"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "selectDocuments", disabled: this.state.signType !== 'Standard' || this.state.names.length === 1, type: "checkbox", value: this.state.selectDocuments, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement("hr", null)
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement(
                                    "b",
                                    null,
                                    "PDF parameters"
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "PDF signature parameters file name: "
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "psp", type: "text", value: this.state.psp, disabled: !singlePdf, onChange: this.handleChange },
                                    this.state.pspFiles.map(function (pspFile) {
                                        return React.createElement(
                                            "option",
                                            { key: pspFile },
                                            pspFile
                                        );
                                    })
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Language of the signature (Acroform): "
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "lang", value: this.state.lang, onChange: this.handleChange, disabled: !singlePdf },
                                    React.createElement(
                                        "option",
                                        { value: "de" },
                                        "Deutsch"
                                    ),
                                    React.createElement(
                                        "option",
                                        { value: "en" },
                                        "English"
                                    ),
                                    React.createElement(
                                        "option",
                                        { value: "fr" },
                                        "Fran\xE7ais"
                                    ),
                                    React.createElement(
                                        "option",
                                        { value: "nl" },
                                        "Nerderlands"
                                    )
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "PDF signature field name: "
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "psfN", type: "text", value: this.state.psfN, disabled: !singlePdf, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "PDF signature field coordinates: "
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "psfC", type: "text", value: this.state.psfC, disabled: !singlePdf, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Include eID photo as icon in the PDF signature field"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "psfP", type: "checkbox", value: this.state.psfP, disabled: !singlePdf, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement("hr", null)
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement(
                                    "b",
                                    null,
                                    "Non PDF parameters"
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "XSLT file name:"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "xslt", value: this.state.xslt, disabled: !singleXML && this.state.signType !== 'XadesMultiFile', onChange: this.handleChange },
                                    this.state.xsltFiles.map(function (xsltFile) {
                                        return React.createElement(
                                            "option",
                                            { key: xsltFile },
                                            xsltFile
                                        );
                                    })
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Policy Id:"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "policyId", value: this.state.policyId, disabled: !singleXML, onChange: this.handleChange },
                                    React.createElement("option", null),
                                    React.createElement(
                                        "option",
                                        null,
                                        "http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/Notary/BE_Justice_Signature_Policy_Notary_eID_Hum_v0.10_202109_Fr.pdf"
                                    ),
                                    React.createElement(
                                        "option",
                                        null,
                                        "http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.5_201512_Nl.pdf"
                                    ),
                                    React.createElement(
                                        "option",
                                        null,
                                        "http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.11_202111_Fr.pdf"
                                    )
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Policy description (Optional):"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "policyDescription", type: "text", value: this.state.policyDescription, onChange: this.handleChange, disabled: !hasPolicy })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Policy Digest Algorithm :"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "policyDigestAlgorithm", value: this.state.policyDigestAlgorithm, onChange: this.handleChange, disabled: !hasPolicy },
                                    this.policyDigestAlgorithms.map(function (algo) {
                                        return React.createElement(
                                            "option",
                                            { key: algo },
                                            algo
                                        );
                                    })
                                )
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement("hr", null)
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement("input", { type: "submit", value: "Submit", disabled: this.state.reasonForNoSubmit }),
                                this.state.reasonForNoSubmit && React.createElement(
                                    "p",
                                    null,
                                    React.createElement(
                                        "label",
                                        { style: { color: 'red' } },
                                        "Submit is disabled because : ",
                                        this.state.reasonForNoSubmit
                                    )
                                ),
                                this.state.signType === 'XadesMultiFile' && React.createElement(
                                    "p",
                                    null,
                                    React.createElement(
                                        "label",
                                        null,
                                        "This will produce a XADES Multifile signature.",
                                        React.createElement("br", null),
                                        "Policies are allowed, XSLT will be used to produce a custom output XML format"
                                    )
                                ),
                                this.state.signType === 'Legacy' && React.createElement(
                                    "p",
                                    null,
                                    React.createElement(
                                        "label",
                                        null,
                                        "Sign a single file with basic options"
                                    )
                                ),
                                this.state.signType === 'Standard' && React.createElement(
                                    "p",
                                    null,
                                    React.createElement(
                                        "label",
                                        null,
                                        "Sign 1 to n files with advanced options"
                                    )
                                )
                            )
                        )
                    )
                )
            );
        }
    }]);

    return NameForm;
}(React.Component);