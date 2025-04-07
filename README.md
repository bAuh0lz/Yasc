# Yasc: _Yet Another Scripter_

Yasc is a Burp Suite extension that enables you to modify HTTP requests by running scripts.

Yasc runs the scripts outside of Burp (JVM) so you can develop them without hassle and can write them in any language (that preferably has gRPC library).

Yasc helps you to develop the scripts by:

- Exporting HTTP requests from Burp and generating script code to send those request
- Providing APIs for sending additional HTTP requests from Burp extension and interacting with Burp Collaborator within the scripts

## Demo

Let's say, you want to repeatedly send an HTTP request `POST /create_item` that:
- Requires a one-time CSRF token
- Creates a new item
- Causes an error if the maximum number of items is reached

What you would need to do is:

1. Update CSRF token in the HTTP request before sending it:

    1. Send a `POST /confirm_item_creation` request to get a new CSRF token
    2. Replace the old CSRF token in the target `POST /create_item` request  with the new one

2. Delete the created item after it is successfully created:

    3. Extract the item ID from the HTTP response
    4. Send a `POST /confirm_item_deletion` request to get a new CSRF token
    5. Send a `POST /delete_item` request with the new CSRF token to delete the item

Here is what the script `create_item.py` to achieve this would look like (see [here](https://github.com/bAuh0lz/yasc_script_runner/blob/main/example/demo/scripts/create_item.py) for more details.):

```py
# 1. Update CSRF token in the HTTP request before sending it:
def run_pre_script(request_to_be_sent: bytes) -> bytes | None:
    # i. Send a `POST /confirm_item_creation` request to get a new CSRF token
    # You can copy the line by Message actions (hamburger button) > Extensions > Yasc > Copy request menu on Burp
    # You can also use another Burp extension like Copy As Python-Requests
    res = util.send_request("https://target.tld", "requests/1.http", {})

    # The CSRF token would be given in the form of `<input type="hidden" name="csrf_token" value="CSRF_TOKEN_VALUE">`.
    csrf_token = util.find_one_by_delimiter(res,'name="csrf_token" value="','">')

    # ii. Replace the old CSRF token in the target `POST /create_item` request  with the new one
    request_to_be_sent = util.update_bytes(request_to_be_sent, {"old_csrf_token": csrf_token})

    # The modified HTTP request will be sent.
    return request_to_be_sent


# - Delete the created item after it is successfully created:
def run_post_script(initiating_request: bytes, response_received: bytes) -> None:
    # iii. Extract the item ID from the HTTP response
    # Retrieve the ID of the created item. The ID would be given in the form of `{"id":"foo"}`.
    item_id=util.find_one_by_delimiter(response_received,'"id":"','"')

    # Check if ID is found. If ID is not found, it means the item was not created then do nothing.
    if not item_id: return

    # iv. Send a `POST /confirm_item_deletion` request to get a new CSRF token
    res = util.send_request("https://target.tld", "requests/2.http", {"foo":item_id})
    csrf_token=util.find_one_by_delimiter(res,'name="csrf_token" value="','">')

    # v. Send a `POST /delete_item` request with the new CSRF token to delete the item
    res = util.send_request("https://target.tld", "requests/3.http", {"foo":item_id, "old_csrf_token":csrf_token})

    return
```

To run the script, the target HTTP request should include the following parameters:

```http
POST /create_item?YASC_PRE_SCRIPT=create_item.py&YASC_POST_SCRIPT=create_item.py HTTP/1.1
...

...&token=old_csrf_token
```

You can now repeatedly send the HTTP request in Burp Repeater or perform active scan safely.


## How Yasc Works

Yasc consists of two key components: the Burp extension and the script runner.

### Burp Extension

The Burp extension checks for specific parameters in HTTP requests:

- `YASC_PRE_SCRIPT=<script name>`: When this parameter is present, the Burp extension sends a gRPC request to the script runner to run the script before sending the target HTTP request.
- `YASC_POST_SCRIPT=<script name>`: When this parameter is present, the Burp extension sends a gRPC request the script runner to run the script after receiving the HTTP response to the target request.

These parameters are stripped from the request before sending it.

Once the Burp extension receives a gRPC response that contains a modified HTTP request, it replaces the original HTTP request with the modified one.

### Script Runner

The script runner runs the scripts when it receives gRPC requests from the Burp extension.

After the script runner finishes the scripts, it replies with a modified HTTP request.


## Getting Started

### Installing

You need to install both the Burp extension and the script runner.

#### Burp Extension

- Download or build the JAR file then install as a Burp extension
- Install it from BApp store

#### Script Runner

You can install the Python3 implementation from pip: `pip install yasc_script_runner` or download from [GitHub](https://github.com/bAuh0lz/yasc_script_runner).

If necessary, use [this](https://github.com/bAuh0lz/yasc_script_runner/tree/main/example/template/scripts) directory as a template of scripts directory.

You may also write your own script runner in any language of your choice.

### Usage

1. Load the Burp extension and note the port number of its gRPC server
2. Launch the script runner:
   - Manual way: Run command `yasc_script_runner -d <scripts dir> -p <the Burp extension server's port>`
   - Automatic way: Set the following environmental variables before launching Burp
     - `YASC_SCRIPT_RUNNER_COMMAND`: Command to launch the script runner
     - `YASC_SCRIPTS_DIR`: Directory where your scripts will be stored
3. Write and save your scripts in the specified scripts directory
4. Add the `YASC_PRE_SCRIPT` and/or `YASC_POST_SCRIPT` parameters to the target HTTP request, with the script name as the value
5. Send the target HTTP request

## Version History

- 1.0.0
    - Initial Release

## License

This project is licensed under the MIT License - see the LICENSE file for details.

